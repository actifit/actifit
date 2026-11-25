package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

//Create a singleton OkHttpClient object

class NetworkClient {
    private static final  OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(130, TimeUnit.SECONDS) // a full workout plan can take ~45s+, and the bot retries a busy model
            .build();
    public static OkHttpClient getInstance(){
        return okHttpClient;
    }
}

/**
 * The AI features, served by the Actifit bot (/ai/*). The app holds no Gemini key: a key
 * built into an APK can be pulled out by anyone who downloads the app, and Google suspended
 * the project after that happened. The bot builds the prompts, holds the key and applies a
 * daily limit per account, so every call needs the user's login session.
 */
public class AiService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final Context app;
    private final OkHttpClient client = NetworkClient.getInstance(); // use singleton client
    private final Gson gson = new GsonBuilder().create();

    public AiService(Context ctx) {
        this.app = ctx.getApplicationContext();
    }

    public interface ResponseCallback {
        void onSuccess(AiResponse response);
        void onFailure(String errorMessage);
    }

    public interface TextResponseCallback {
        void onSuccess(String text);
        void onFailure(String errorMessage);
    }

    public interface CancellableRequest {
        void cancel();
    }

    /** A bot /ai/* reply: the text, or a message to show, or a refused session. */
    static final class Reply {
        final String text;
        final String error;
        final boolean auth;

        private Reply(String text, String error, boolean auth) {
            this.text = text;
            this.error = error;
            this.auth = auth;
        }
    }

    /** Classifies a bot /ai/* reply; {@code fallback} is shown when the bot gives no message. */
    static Reply parseReply(int httpCode, String body, String fallback) {
        try {
            JSONObject o = new JSONObject(body);
            if (BadgesApi.isSessionError(o)) return new Reply(null, null, true);
            String text = o.optString("text", "");
            if (httpCode == 200 && "success".equals(o.optString("status")) && !text.trim().isEmpty()) {
                return new Reply(text.trim(), null, false);
            }
            String message = o.optString("message", "");
            return new Reply(null, message.isEmpty() ? fallback : message, false);
        } catch (Exception e) {
            // not JSON: the rate limiter's text page, a proxy error page
            return new Reply(null, fallback, false);
        }
    }

    public void generateWorkoutPlan(WorkoutRequest workoutRequest, final ResponseCallback callback) {
        JSONObject body = new JSONObject();
        try {
            JSONObject r = new JSONObject();
            r.put("fitnessGoal", workoutRequest.getFitnessGoal());
            r.put("experienceLevel", workoutRequest.getExperienceLevel());
            r.put("weeklyTime", workoutRequest.getWeeklyTime());
            r.put("dailyFrequency", workoutRequest.getDailyFrequency());
            r.put("preferredWorkout", workoutRequest.getPreferredWorkout());
            r.put("equipment", workoutRequest.getEquipment());
            r.put("limitations", workoutRequest.getLimitations());
            r.put("otherLimitations", capped(workoutRequest.getOtherLimitations(), 500));
            body.put("request", r);
        } catch (Exception e) {
            callback.onFailure(app.getString(R.string.ai_failed));
            return;
        }
        post("ai/workoutPlan", body, true, new Call[1], new boolean[1], new TextResponseCallback() {
            @Override
            public void onSuccess(String text) {
                try {
                    callback.onSuccess(parseWorkoutPlanFromContent(extractJsonFromContentString(text)));
                } catch (Exception e) {
                    callback.onFailure(app.getString(R.string.ai_failed));
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    /** Translates a post into English; the bot fetches the post itself, by author and permlink. */
    public void translatePost(String author, String permlink, final TextResponseCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("author", author);
            body.put("permlink", permlink);
        } catch (Exception e) {
            callback.onFailure(app.getString(R.string.ai_failed));
            return;
        }
        post("ai/translate", body, true, new Call[1], new boolean[1], callback);
    }

    public CancellableRequest generateChatResponse(List<ChatMessage> history, final TextResponseCallback callback) {
        final Call[] currentCall = new Call[1];
        final boolean[] canceled = {false};
        JSONObject body = new JSONObject();
        try {
            JSONArray turns = new JSONArray();
            for (ChatMessage message : history) {
                turns.put(new JSONObject().put("role", message.getRole()).put("text", message.getText()));
            }
            body.put("history", turns);
        } catch (Exception e) {
            callback.onFailure(app.getString(R.string.ai_failed));
            return () -> { };
        }
        post("ai/chat", body, true, currentCall, canceled, callback);
        return () -> {
            canceled[0] = true;
            if (currentCall[0] != null) {
                currentCall[0].cancel();
            }
        };
    }

    /**
     * POSTs to the bot with the session token. A refused session is renewed once (the stored
     * posting key logs in again) and the call sent again.
     */
    private void post(String path, JSONObject json, boolean mayRefresh, Call[] currentCall, boolean[] canceled,
                      TextResponseCallback callback) {
        if (canceled[0]) return;
        String token = LoginActivity.accessToken;
        if (token == null || token.isEmpty()) {
            if (mayRefresh) refreshThenPost(path, json, currentCall, canceled, callback);
            else callback.onFailure(app.getString(R.string.ai_login_needed));
            return;
        }
        Request request = new Request.Builder()
                .url(Utils.apiUrl(app) + path)
                .addHeader(app.getString(R.string.validation_header),
                        app.getString(R.string.validation_pre_data) + " " + token)
                .post(RequestBody.create(json.toString(), JSON))
                .build();
        Call call = client.newCall(request);
        currentCall[0] = call;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled() || canceled[0]) return;
                callback.onFailure(app.getString(R.string.ai_network_error));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body;
                try {
                    body = response.body() != null ? response.body().string() : "";
                } finally {
                    response.close();
                }
                if (call.isCanceled() || canceled[0]) return;
                Reply r = parseReply(response.code(), body, app.getString(R.string.ai_failed));
                if (r.auth) {
                    if (mayRefresh) refreshThenPost(path, json, currentCall, canceled, callback);
                    else callback.onFailure(app.getString(R.string.ai_login_needed));
                } else if (r.text != null) {
                    callback.onSuccess(r.text);
                } else {
                    callback.onFailure(r.error);
                }
            }
        });
    }

    private void refreshThenPost(String path, JSONObject json, Call[] currentCall, boolean[] canceled,
                                 TextResponseCallback callback) {
        // Volley delivers on the main thread; OkHttp calls back on its own
        new Handler(Looper.getMainLooper()).post(() -> BadgesApi.refreshToken(app, MainActivity.username, ok -> {
            if (canceled[0]) return;
            if (ok) post(path, json, false, currentCall, canceled, callback);
            else callback.onFailure(app.getString(R.string.ai_login_needed));
        }));
    }

    static String capped(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    static String extractJsonFromContentString(String content){
        if (content == null) {
            return null;
        }

        content = content.trim();

        if (content.startsWith("```json")){
            content = content.substring(7);
        }

        if (content.endsWith("```")){
            content = content.substring(0,content.length() - 3);
        }
        return content.trim();
    }


    private AiResponse parseWorkoutPlanFromContent(String content) {
        try {
            Map<String, Object> contentMap = gson.fromJson(content, Map.class);

            if(contentMap == null || !contentMap.containsKey("workoutPlan") || !contentMap.containsKey("explanation")){
                throw new Exception("Invalid content format, missing workoutPlan or explanation");
            }

            Map<String, Object> workoutPlanMap = (Map<String, Object>) contentMap.get("workoutPlan");

            if(workoutPlanMap == null || !workoutPlanMap.containsKey("description") || !workoutPlanMap.containsKey("exercises")){
                throw new Exception("Invalid workoutPlan format, missing description or exercises");
            }

            String description = (String) workoutPlanMap.get("description");
            List<Map<String, Object>> exercisesMap = (List<Map<String, Object>>) workoutPlanMap.get("exercises");

            if (exercisesMap == null || exercisesMap.isEmpty()) {
                throw new Exception("No exercises found in workout plan");
            }

            List<Exercise> exerciseList = new ArrayList<>();

            for (Map<String, Object> exerciseMap : exercisesMap) {

                if(exerciseMap == null || !exerciseMap.containsKey("name") || !exerciseMap.containsKey("sets") || !exerciseMap.containsKey("reps")|| !exerciseMap.containsKey("days")){
                    throw new Exception("Invalid exercise format, missing name, sets, reps, or days");
                }


                String exerciseName = (String) exerciseMap.get("name");

                String sets =  (String) exerciseMap.get("sets");
                String reps =  (String) exerciseMap.get("reps");
                String duration = (String) exerciseMap.get("duration");

                List<String> days = parseArray(exerciseMap.get("days"));

                exerciseList.add(new Exercise(exerciseName, sets, reps, duration, null, days));
            }

            WorkoutPlan workoutPlan = new WorkoutPlan(exerciseList, description);
            String explanation = (String) contentMap.get("explanation");

            return new AiResponse(workoutPlan, explanation);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing the workout plan: " + e.getMessage());
        }
    }

    private List<String> parseArray(Object arrayObject) {
        List<String> daysList = new ArrayList<>();

        if (arrayObject instanceof List) {
            List<?> rawList = (List<?>) arrayObject;
            for (Object item : rawList) {
                if (item instanceof String) {
                    daysList.add((String) item);
                }
            }
        } else if(arrayObject instanceof String){
            String daysString = (String) arrayObject;
            String[] daysArray = daysString.split(",");
            for (String day : daysArray) {
                daysList.add(day.trim());
            }
        }

        return daysList;
    }
}
