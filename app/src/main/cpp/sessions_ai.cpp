#include <android/log.h>
#include <jni.h>
#include <sstream>

#define LOG_TAG "SessionsAI-JNI"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#include <sstream>
#include <string>
#include <vector>

#include "chat.h"
#include "common.h"
#include "llama.h"
#include "sampling.h"

// Globals
static llama_model *g_model = nullptr;
static llama_context *g_context = nullptr;
static llama_batch g_batch;
static common_sampler *g_sampler = nullptr;

static std::vector<llama_token> cached_prompt_tokens;
static llama_pos current_position = 0;
static llama_pos stop_generation_position = 0;
static std::string cached_token_chars;

constexpr int DEFAULT_CONTEXT_SIZE = 8192;
constexpr int BATCH_SIZE = 512;
constexpr int OVERFLOW_HEADROOM = 4;
constexpr float DEFAULT_SAMPLER_TEMP = 0.7f;

extern "C" JNIEXPORT void JNICALL Java_com_sessions_1ai_SessionsAIEngine_init(
    JNIEnv *env, jobject /*unused*/, jstring nativeLibDir) {
  // llama_log_set(aichat_android_log_callback, nullptr); // Removed as
  // logging.h is replaced

  const auto *path_to_backend = env->GetStringUTFChars(nativeLibDir, 0);
  LOGi("Loading backends from %s", path_to_backend);
  ggml_backend_load_all_from_path(path_to_backend);
  env->ReleaseStringUTFChars(nativeLibDir, path_to_backend);

  llama_backend_init();
  LOGi("Backend initiated.");
}

extern "C" JNIEXPORT jint JNICALL Java_com_sessions_1ai_SessionsAIEngine_load(
    JNIEnv *env, jobject, jstring jmodel_path) {
  llama_model_params model_params = llama_model_default_params();
  const auto *model_path = env->GetStringUTFChars(jmodel_path, 0);
  LOGi("Loading model from: %s", model_path);

  g_model = llama_model_load_from_file(model_path, model_params);
  env->ReleaseStringUTFChars(jmodel_path, model_path);
  if (!g_model)
    return 1;

  llama_context_params ctx_params = llama_context_default_params();
  ctx_params.n_ctx = DEFAULT_CONTEXT_SIZE;
  ctx_params.n_batch = BATCH_SIZE;
  ctx_params.n_ubatch = BATCH_SIZE;
  g_context = llama_init_from_model(g_model, ctx_params);
  if (!g_context)
    return 2;

  g_batch = llama_batch_init(BATCH_SIZE, 0, 1);

  common_params_sampling sparams;
  sparams.temp = DEFAULT_SAMPLER_TEMP;
  g_sampler = common_sampler_init(g_model, sparams);

  return 0;
}

static void shift_context() {
  const int n_discard = current_position / 2;
  LOGi("Context full. Discarding %d tokens.", n_discard);
  llama_memory_seq_rm(llama_get_memory(g_context), 0, 0, n_discard);
  llama_memory_seq_add(llama_get_memory(g_context), 0, n_discard,
                       current_position, -n_discard);
  current_position -= n_discard;
}

static int decode_tokens_in_batches(llama_context *context, llama_batch &batch,
                                    const std::vector<llama_token> &tokens,
                                    const llama_pos start_pos) {
  for (int i = 0; i < (int)tokens.size(); i += BATCH_SIZE) {
    const int cur_batch_size = std::min((int)tokens.size() - i, BATCH_SIZE);
    common_batch_clear(batch);

    if (start_pos + i + cur_batch_size >=
        DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
      shift_context();
    }

    for (int j = 0; j < cur_batch_size; j++) {
      const llama_token token_id = tokens[i + j];
      const llama_pos position = start_pos + i + j;
      const bool want_logit = (i + j == tokens.size() - 1);
      common_batch_add(batch, token_id, position, {0}, want_logit);
    }

    if (llama_decode(context, batch)) {
      LOGe("llama_decode failed.");
      return 1;
    }
  }
  return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sessions_1ai_SessionsAIEngine_processPrompt(JNIEnv *env,
                                                     jobject /*unused*/,
                                                     jstring jprompt,
                                                     jint n_predict) {
  const auto *prompt = env->GetStringUTFChars(jprompt, nullptr);
  std::string prompt_str(prompt);
  env->ReleaseStringUTFChars(jprompt, prompt);

  auto user_tokens = common_tokenize(g_context, prompt_str, true, true);

  size_t common_prefix_len = 0;
  while (common_prefix_len < cached_prompt_tokens.size() &&
         common_prefix_len < user_tokens.size() &&
         cached_prompt_tokens[common_prefix_len] ==
             user_tokens[common_prefix_len]) {
    common_prefix_len++;
  }

  LOGi("SessionsAIEngine: Reusing %zu tokens from KV cache.",
       common_prefix_len);
  if (common_prefix_len < cached_prompt_tokens.size()) {
    llama_memory_seq_rm(llama_get_memory(g_context), 0, common_prefix_len, -1);
    cached_prompt_tokens.resize(common_prefix_len);
    current_position = common_prefix_len;
  }

  std::vector<llama_token> new_tokens(user_tokens.begin() + common_prefix_len,
                                      user_tokens.end());
  LOGi("SessionsAIEngine: Evaluating %zu new tokens.", new_tokens.size());

  const int max_batch_size = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM;
  if (current_position + new_tokens.size() > max_batch_size) {
    shift_context();
  }

  if (decode_tokens_in_batches(g_context, g_batch, new_tokens,
                               current_position)) {
    LOGe("llama_decode() failed!");
    return 1;
  }

  for (auto id : new_tokens) {
    cached_prompt_tokens.push_back(id);
  }
  current_position += new_tokens.size();
  stop_generation_position = current_position + n_predict;

  return 0;
}

static bool is_valid_utf8(const char *string) {
  if (!string)
    return true;
  const auto *bytes = (const unsigned char *)string;
  int num;
  while (*bytes != 0x00) {
    if ((*bytes & 0x80) == 0x00)
      num = 1;
    else if ((*bytes & 0xE0) == 0xC0)
      num = 2;
    else if ((*bytes & 0xF0) == 0xE0)
      num = 3;
    else if ((*bytes & 0xF8) == 0xF0)
      num = 4;
    else
      return false;
    bytes += 1;
    for (int i = 1; i < num; ++i) {
      if ((*bytes & 0xC0) != 0x80)
        return false;
      bytes += 1;
    }
  }
  return true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sessions_1ai_SessionsAIEngine_generateNextToken(JNIEnv *env,
                                                         jobject /*unused*/) {
  if (current_position >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM)
    shift_context();
  if (current_position >= stop_generation_position)
    return nullptr;

  const auto new_token_id = common_sampler_sample(g_sampler, g_context, -1);
  common_sampler_accept(g_sampler, new_token_id, true);

  common_batch_clear(g_batch);
  common_batch_add(g_batch, new_token_id, current_position, {0}, true);
  if (llama_decode(g_context, g_batch) != 0)
    return nullptr;

  current_position++;
  cached_prompt_tokens.push_back(new_token_id);

  if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id))
    return nullptr;

  auto new_token_chars = common_token_to_piece(g_context, new_token_id);
  cached_token_chars += new_token_chars;

  jstring result = nullptr;
  if (is_valid_utf8(cached_token_chars.c_str())) {
    result = env->NewStringUTF(cached_token_chars.c_str());
    cached_token_chars.clear();
  } else {
    result = env->NewStringUTF("");
  }
  return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sessions_1ai_SessionsAIEngine_unload(JNIEnv *, jobject /*unused*/) {
  cached_prompt_tokens.clear();
  current_position = 0;

  if (g_sampler)
    common_sampler_free(g_sampler);
  if (g_context) {
    llama_batch_free(g_batch);
    llama_free(g_context);
  }
  if (g_model)
    llama_model_free(g_model);

  g_sampler = nullptr;
  g_context = nullptr;
  g_model = nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sessions_1ai_SessionsAIEngine_shutdown(JNIEnv *, jobject /*unused*/) {
  llama_backend_free();
}
