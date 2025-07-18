package io.actifit.fitnesstracker.actifitfitnesstracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

//Create a singleton OkHttpClient object

class NetworkClient {
    private static final  OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    public static OkHttpClient getInstance(){
        return okHttpClient;
    }
}

public class AiService {


    private static final String API_KEY = BuildConfig.GEMINI_API_KEY; // **REPLACE WITH YOUR ACTUAL API KEY!**
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + API_KEY;
    //private final OkHttpClient client = new OkHttpClient();
    private final OkHttpClient client = NetworkClient.getInstance(); // use singleton client

    private final Gson gson = new GsonBuilder().create();


    public interface ResponseCallback {
        void onSuccess(AiResponse response);
        void onFailure(String errorMessage);
    }

    public void generateWorkoutPlan(WorkoutRequest workoutRequest, final ResponseCallback callback) {
        RequestBody requestBody = createRequestBody(workoutRequest);

        Request request = new Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                android.util.Log.e("AiService", "OkHttp Failure", e); // <-- LOG EXCEPTION
                callback.onFailure("Failed " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBodyString = response.body().string(); // Read body ONCE
                if (response.isSuccessful()) {
                    try {
                        android.util.Log.d("AiService", "Success Response Body: " + responseBodyString);

                        //String responseBody = response.body().string();
                        AiResponse aiResponse = parseAiResponse(responseBodyString);
                        callback.onSuccess(aiResponse);
                    } catch (Exception e) {
                        callback.onFailure("Error parsing response " + e.getMessage());
                    }

                } else {
                    // Log the error response details!
                    String errorMessage = "Response failed: " + response.message() + " (" + response.code() + ")";
                    android.util.Log.e("AiService", errorMessage + " Body: " + responseBodyString); // <-- LOG ERROR BODY
                    callback.onFailure("Response failed: " + response.message() + " " + response.code());
                }

            }
        });
    }


    private RequestBody createRequestBody(WorkoutRequest workoutRequest) {

        String prompt = generatePrompt(workoutRequest);

        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("parts", List.of(Map.of("text", prompt)));
        contents.add(userMessage);

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("contents", contents);

        String requestJson = gson.toJson(requestBodyMap);
        android.util.Log.d("AiService", "Request JSON: " + requestJson); // <-- ADD THIS LOG
        return RequestBody.create(
                requestJson,
                MediaType.parse("application/json; charset=utf-8")
        );
    }



    //im adding the ai part here
    public void generateFromFreePrompt(String prompt, final ResponseCallback callback) {
        RequestBody requestBody = createFreePromptRequestBody(prompt);

        Request request = new Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("Failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        Map<String, Object> responseMap = gson.fromJson(responseBody, Map.class);
                        List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
                        if (candidates != null && !candidates.isEmpty()) {
                            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                            String result = (String) parts.get(0).get("text");
                            callback.onSuccess(new AiResponse(result));
                        } else {
                            callback.onFailure("No AI response");
                        }
                    } catch (Exception e) {
                        callback.onFailure("Error parsing response: " + e.getMessage());
                    }
                } else {
                    callback.onFailure("Request failed: " + response.message());
                }
            }
        });
    }

    private RequestBody createFreePromptRequestBody(String prompt) {
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("parts", List.of(Map.of("text", prompt)));
        contents.add(userMessage);

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("contents", contents);

        String json = gson.toJson(requestBodyMap);
        return RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
    }//i added this, too



    private AiResponse parseAiResponse(String responseBody) {
        try {
            Map<String, Object> responseMap = gson.fromJson(responseBody, Map.class);
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");

            if (candidates != null && !candidates.isEmpty()) {

                Map<String, Object> firstCandidate = candidates.get(0);

                if (firstCandidate != null && firstCandidate.containsKey("content")){
                    Object contentObject = firstCandidate.get("content");
                    if (contentObject instanceof Map){
                        Map<String, Object> contentMap = (Map<String, Object>) contentObject;
                        if (contentMap.containsKey("parts")){
                            Object partsObject = contentMap.get("parts");
                            if (partsObject instanceof List){
                                List<Map<String,Object>> contentParts = (List<Map<String, Object>>) partsObject;
                                if (!contentParts.isEmpty()){
                                    String content = (String) contentParts.get(0).get("text");

                                    String extractedContent = extractJsonFromContentString(content);

                                    System.out.println(">>>outcome of AI query:"+extractedContent);

                                    return parseWorkoutPlanFromContent(extractedContent);
                                } else {
                                    throw new Exception("No parts in response");
                                }

                            } else {
                                throw new Exception("Parts is not a list");
                            }
                        }  else {
                            throw new Exception("No parts present in content");
                        }
                    } else {
                        throw new Exception("Content is not a map");
                    }
                } else{
                    throw new Exception("No content present in first candidate");
                }

            } else {
                throw new Exception("No candidates present in response");
            }
        } catch (Exception e) {
            // Handle the parsing error
            throw new RuntimeException("Error parsing AI response " + e.getMessage());
        }
    }

    private String extractJsonFromContentString(String content){
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

                //String imageUrl = parseArray(exerciseMap.get("imageUrl"));

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

    private String safeParseString(Object value) {
        return value instanceof String ? (String) value : null;
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



    // creating the prompt
    private String generatePrompt(WorkoutRequest request) {
        String prompt = "I am a user with the following fitness details. My fitness goal is to: " +
                request.getFitnessGoal() + ". I would describe my current experience level as: "
                + request.getExperienceLevel() + ". I can allocate " + request.getWeeklyTime() + " to workouts each week. "
                + "I intend to work out " + request.getDailyFrequency() + " per week. "
                + "I generally prefer " + request.getPreferredWorkout() + " style workouts. " +
                "I have the following equipment: " + request.getEquipment() ;


        if (request.getLimitations() != null && !request.getLimitations().isEmpty() ){
            prompt +=   ". And my physical limitations are: " + request.getLimitations();
        }
        if (request.getOtherLimitations() != null && !request.getOtherLimitations().isEmpty() ){
            prompt +=   ". with the following details on limitations: " + request.getOtherLimitations();
        }

        prompt +=   ". Based on this information can you give me a detailed workout plan and a brief explanation for it." +
                "Response should be in JSON format with the fields workoutPlan and explanation. "+
                "workoutPlan should have description and a list of exercises each with name, reps, sets, "+
                //+ "and duration.";
                "duration, "+
                //"and a field imageUrl which should contain a publicly available url for an image representing this exercise, "+
                "and a field days, specifying a list of days the exercise is to be performed on. "+
                //imageUrl
                "Ensure that all string values for name, sets, reps, duration, and days should be wrapped in double quotes.";

        return prompt;
    }
}