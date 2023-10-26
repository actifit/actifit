package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.bitcoinj.core.Base58;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.VarInt;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.util.encoders.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;


import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.TAG;

public class HiveRequests {

    public String hiveRPCUrl;
    private Context ctx;
    private RequestQueue queue;
    private JSONObject options;
    static String chainId = "beeab0de00000000000000000000000000000000000000000000000000000000";


    public HiveRequests(Context ctx) {
        this.ctx = ctx;
        //default RPC node
        hiveRPCUrl = ctx.getString(R.string.hive_default_node);
        // Instantiate the RequestQueue.
        queue = Volley.newRequestQueue(ctx);
    }

    public void setOptions(JSONObject options){
        this.options = options;
        if (options.has("setRPC")){
            try {
                this.hiveRPCUrl = options.getString("setRPC");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /*public void votePost(){
        performAPIRequest(ctx.getString(R.string.live_server),
                "{\"jsonrpc\":\"2.0\", \"method\":\"database_api.get_dynamic_global_properties\", \"id\":1}",
        new APIResponseListener() {
            @Override
            public void onResponse(JSONObject dynamicProps) {
                // Step 5: Perform another API call
                performAnotherAPIRequest(dynamicProps);
            }

            @Override
            public void onError(String errorMessage) {
                // Handle the error
            }
        });
    }*/

    public void getGlobalProps(final APIResponseListener listener) {
        try {
            performAPIRequest("https://api.hive.blog",
                    "{\"jsonrpc\":\"2.0\", \"method\":\"database_api.get_dynamic_global_properties\", \"id\":1}",listener);

            /*
            *
                    new APIResponseListener() {
                        @Override
                        public void onResponse(JSONObject dynamicProps) {
                            // Step 5: Perform another API call
                            performAnotherAPIRequest(dynamicProps);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            // Handle the error
                        }
                    }
            * */

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /*
    @TargetApi(Build.VERSION_CODES.N)
    public JSONObject getGlobalProps(){
        CompletableFuture<JSONObject> future = this.grabGlobProps();

        try {
            JSONObject response = future.join();
            return response;
            // Handle the response
        } catch (Exception e) {
            e.printStackTrace();
            // Handle the exception
        }
        return new JSONObject();
    }

    @TargetApi(Build.VERSION_CODES.N)
    public CompletableFuture<JSONObject> grabGlobProps() {
        CompletableFuture<JSONObject> future = new CompletableFuture<>();

        String url = "https://api.hive.blog";

        // Create the JSON object for the request body
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("jsonrpc", "2.0");
            jsonBody.put("method", "database_api.get_dynamic_global_properties");
            jsonBody.put("id", 1);
        } catch (JSONException e) {
            e.printStackTrace();
            future.completeExceptionally(e);
            return future;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, jsonBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        future.complete(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        future.completeExceptionally(error);
                    }
                });

        // Add the request to the RequestQueue
        RequestQueue requestQueue = Volley.newRequestQueue(ctx);
        requestQueue.add(request);

        return future;
    }*/


    @TargetApi(Build.VERSION_CODES.N)
    public JSONArray getComments(JSONObject params) {
        JSONArray outcome = new JSONArray();
        CompletableFuture<JSONArray> future = this.processRequest(ctx.getString(R.string.get_post_comments), params);
        try {
            JSONArray result = future.join(); // Waits for the future to complete and returns the result
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return outcome;
        }
    }


    @TargetApi(Build.VERSION_CODES.N)
    public JSONArray getRankedPosts(JSONObject params){
        JSONArray outcome = new JSONArray();
        CompletableFuture<JSONArray> future = this.processRequest(ctx.getString(R.string.get_ranked_posts), params);
        try {
            JSONArray result = future.join(); // Waits for the future to complete and returns the result
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return outcome;
        }
    }




    @TargetApi(Build.VERSION_CODES.N)
    public CompletableFuture<JSONArray> processRequest(String method, JSONObject params) {
        CompletableFuture<JSONArray> future = new CompletableFuture<>();
        JsonObjectRequest request;
        try {
            // Request the transactions of the user first via JsonArrayRequest
            // according to our data format
            request = new JsonObjectRequest(Request.Method.POST,
                    hiveRPCUrl, null, response -> {
                try {
                    JSONArray postArray = response.getJSONArray("result");
                    future.complete(postArray);
                    //future.complete(response);
                    //return future;
                } catch (JSONException e) {
                //} catch (Exception e) {
                    e.printStackTrace();
                }
            },error -> {
                    error.printStackTrace();
                    future.completeExceptionally(error);
            }){
                @Override
                public byte[] getBody() {
                    return prepareJSONReq(method, params);
                }
                @Override
                public String getBodyContentType() {
                    return "application/json";
                }

            };

            //transactionRequest.setRetryPolicy(new DefaultRetryPolicy(15000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            // Set the timeout
            int timeoutMs = 15000; // 15 seconds
            request.setRetryPolicy(new DefaultRetryPolicy(
                    timeoutMs,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

            // Add transaction request to be processed
            queue.add(request);

        }catch(Exception exception){
            exception.printStackTrace();
            future.completeExceptionally(exception);
        }
        //return future;
        return future;
    }

    private byte[] prepareJSONReq(String method, JSONObject params){
        JSONObject jsonRequest = new JSONObject();
        try {
            jsonRequest.put("jsonrpc", "2.0");

            jsonRequest.put("method", method);
            if (params!=null) {
                jsonRequest.put("params", params);
            }
            jsonRequest.put("id", 1);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonRequest.toString().getBytes();
    }

    private void performAPIRequest(String url, String request, final APIResponseListener listener) throws JSONException {
        //RequestQueue requestQueue = Volley.newRequestQueue(ctx );

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                url,
                new JSONObject(request),
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            // Step 2: Handle the API response
                            JSONObject dynamicProps = response.getJSONObject("result");

                            if (dynamicProps != null) {


                                // Step 5: Invoke the listener with the serialized transaction
                                if (listener != null) {
                                    listener.onResponse(dynamicProps);
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "JSONException: " + e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Volley request failed with error: " + error.getMessage());
                        if (listener != null) {
                            listener.onError(error.getMessage());
                        }
                    }
                }
        );

        queue.add(jsonObjectRequest);
    }


    private static byte[] signTransaction(byte[] digest, byte[] privateKey) {
        ECKey ecKey = ECKey.fromPrivate(privateKey);


        //Sha256Hash messageAsHash = Sha256Hash.of(hexStringToByteArray(chainId));
        //ecKey.signMessage()
        //String signature = ecKey.signMessage(String.valueOf(messageAsHash));

        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        ECPrivateKeyParameters privateKeyParams = null;
        try {
            privateKeyParams = (ECPrivateKeyParameters) PrivateKeyFactory.createKey(privateKey);
        } catch (IOException e) {
            e.printStackTrace();
        }

        /*
        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        ECDomainParameters domainParams = getCurveDomainParameters(); //(ecKey.getCurve().getCurveName());
        ECPrivateKeyParameters privateKeyParams = new ECPrivateKeyParameters(ecKey.getPrivKey(), );//, domainParams);
        */

/*
        for (ECKey requiredPrivateKey : getRequiredSignatureKeys()) {
            boolean isCanonical = false;

            Sha256Hash messageAsHash;
            while (!isCanonical) {
                try {
                    messageAsHash = Sha256Hash.of(this.toByteArray(chainId));
                } catch (SteemInvalidTransactionException e) {
                    throw new SteemInvalidTransactionException(
                            "The required encoding is not supported by your platform.", e);
                }

                String signature = requiredPrivateKey.signMessage(messageAsHash);
                byte[] signatureAsByteArray = Base64.decode(signature);

                if (isCanonical(signatureAsByteArray)) {
                    getExpirationDate().setDateTime(this.getExpirationDate().getDateTimeAsTimestamp() + 1);
                } else {
                    isCanonical = true;
                    this.signatures.add(CryptoUtils.HEX.encode(signatureAsByteArray));
                }
            }
        }

        public List<ECKey> getRequiredSignatureKeys()  {
            List<ECKey> requiredSignatures = new ArrayList<>();
            Map<SignatureObject, PrivateKeyType> requiredAuthorities = getRequiredAuthorities();

            for (Map.Entry<SignatureObject, PrivateKeyType> requiredAuthority : requiredAuthorities.entrySet()) {
                if (requiredAuthority.getKey() instanceof AccountName) {
                    requiredSignatures = getRequiredSignatureKeyForAccount(requiredSignatures,
                            (AccountName) requiredAuthority.getKey(), requiredAuthority.getValue());
                } else if (requiredAuthority.getKey() instanceof Authority) {
                    // TODO: Support authorities.
                } else {
                    LOGGER.warn("Unknown SigningObject type {}", requiredAuthority.getKey());
                }
            }

            return requiredSignatures;
        }*/



        //ECPrivateKeyParameters privateKeyParams = new ECPrivateKeyParameters(ecKey.getPrivKey(), ECKey.CURVE.getCurve());
        /* */
        signer.init(true, privateKeyParams);


        BigInteger[] signature = signer.generateSignature(digest);

        //byte[] stringByteArr = hexStringToByteArray(signature);
        byte[] r = signature[0].toByteArray();
        byte[] s = signature[1].toByteArray();

        // Ensure that r and s are 32 bytes each
        if (r.length < 32) {
            r = Arrays.copyOfRange(r, 0, r.length);
        }
        if (s.length < 32) {
            s = Arrays.copyOfRange(s, 0, s.length);
        }

        return ByteBuffer.allocate(64)
                .put(r, r.length - 32, 32)
                .put(s, s.length - 32, 32)
                .array();


    }

    /*private static boolean isCanonicalSignature(byte[] signature) {
        return !(signature[0] < 0x21 || signature[0] > 0x60)
                && !(signature[32] < 0x21 || signature[32] > 0x60);
    }*/

    @TargetApi(Build.VERSION_CODES.N)
    private static void includeSignatureInTransaction(String signature) {
        // Replace with your implementation of including the signature in the transaction object
        // Modify the signedTransaction object as per your requirements
        // Here, I'm assuming signedTransaction is a Map<String, Object> for simplicity
        Map<String, Object> signedTransaction = new HashMap<>();
        signedTransaction.put("signature", signature);
        // Add the signature to the transaction object
        List<String> signatures = (List<String>) signedTransaction.getOrDefault("signatures", new ArrayList<>());
        signatures.add(signature);
        signedTransaction.put("signatures", signatures);

        System.out.println("Signed Transaction: " + signedTransaction);
    }

    private static boolean isCanonicalSignature(byte[] signature) {
        return ((signature[0] & 0x80) != 0) || (signature[0] == 0) || ((signature[1] & 0x80) != 0)
                || ((signature[32] & 0x80) != 0) || (signature[32] == 0) || ((signature[33] & 0x80) != 0);
    }

    private static byte[] getPrivateKey(String key) {
        byte[] keyBuffer = Base58.decode(key);
        return extractPrivateKey(keyBuffer);
    }

    private static byte[] extractPrivateKey(byte[] keyBuffer) {
        // Remove the last 4 bytes from the key buffer
        byte[] truncatedKeyBuffer = Arrays.copyOfRange(keyBuffer, 0, keyBuffer.length - 4);

        // Remove the first byte from the truncated key buffer
        byte[] privateKey = Arrays.copyOfRange(truncatedKeyBuffer, 1, truncatedKeyBuffer.length);

        return privateKey;
    }

    private static byte[] createDigest(byte[] transactionData) {
        try {
            Security.addProvider(new BouncyCastleProvider());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(transactionData);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static byte[] hexStringToByteArray(String hex) {
        int length = hex.length();
        byte[] data = new byte[length / 2];

        for (int i = 0; i < length; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) +
                    Character.digit(hex.charAt(i + 1), 16));
        }

        return data;
    }

    private String byteArrayToHexString(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private JSONObject createTransactionJson(int refBlockNum, int refBlockPrefix) throws JSONException {
        // Step 3: Create the transaction JSON
        JSONObject transaction = new JSONObject();
        transaction.put("ref_block_num", refBlockNum);
        transaction.put("ref_block_prefix", refBlockPrefix);

        // Calculate expiration time
        long expireTime = 1000 * 60;
        long expiration = System.currentTimeMillis() + expireTime;
        transaction.put("expiration", expiration);

        // Prepare operations array
        JSONObject operation = new JSONObject();
        operation.put("voter", "guest123");
        operation.put("author", "guest123");
        operation.put("permlink", "20191107t125713486z-post");
        operation.put("weight", 9900);

        JSONArray operationsArray = new JSONArray();
        operationsArray.put(operation);
        transaction.put("operations", operationsArray);

        // Prepare extensions array
        //JSONObject[] extensionsArray = new JSONObject[]{};
        JSONArray extensionsArray = new JSONArray();
        transaction.put("extensions", extensionsArray);

        return transaction;
    }

    private byte[] serializeTransaction(JSONObject transaction) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
        try {
            int refBlockNum = transaction.getInt("ref_block_num");
            int refBlockPrefix = transaction.getInt("ref_block_prefix");
            long expiration = transaction.getLong("expiration");
            JSONArray operationsArray = transaction.getJSONArray("operations");
            JSONArray extensionsArray = transaction.getJSONArray("extensions");

            // Convert refBlockNum to bytes
            byte[] refBlockNumBytes = new byte[]{(byte) (refBlockNum & 0xFF), (byte) ((refBlockNum >> 8) & 0xFF)};

            dataOutputStream.write(refBlockNumBytes);
            dataOutputStream.writeInt(refBlockPrefix);
            dataOutputStream.writeInt((int) expiration);

            dataOutputStream.writeByte(operationsArray.length()); // number of operations

            //VOTING OPERATION SAMPLE
            dataOutputStream.write(transformIntToVarIntByteArray(OperationType.VOTE_OPERATION.getOpId())); // operation id
            JSONObject operation = operationsArray.getJSONObject(0);
            writeVString(dataOutputStream, operation.getString("voter"));
            writeVString(dataOutputStream, operation.getString("author"));
            writeVString(dataOutputStream, operation.getString("permlink"));
            dataOutputStream.writeShort(operation.getInt("weight"));

            dataOutputStream.writeByte(extensionsArray.length()); // number of extensions

            // Optional: Serialize extensions if needed
            // writeVString(dataOutputStream, extensionsArray.getString(0));

            dataOutputStream.flush();
        } catch (IOException | JSONException e) {
            Log.e(TAG, "IOException or JSONException: " + e.getMessage());
        }

        return byteArrayOutputStream.toByteArray();
    }


    private void writeVString(DataOutputStream dataOutputStream, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarint32(dataOutputStream, bytes.length);
        dataOutputStream.write(bytes);
    }

    private void writeVarint32(DataOutputStream dataOutputStream, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            dataOutputStream.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        dataOutputStream.writeByte(value & 0x7F);
    }

    public static byte[] transformShortToByteArray(short shortValue) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(shortValue).array();
    }

    public static byte[] transformIntToVarIntByteArray(int intValue) {
        try {
            int value = intValue;

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            DataOutput out = new DataOutputStream(byteArrayOutputStream);

            while ((value & 0xFFFFFF80) != 0L) {
                out.writeByte((value & 0x7F) | 0x80);
                value >>>= 7;
            }

            out.writeByte(intValue & 0x7F);

            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            Log.e (TAG,"Could not transform the given int value into its VarInt representation - "
                    + "Using BitcoinJ as Fallback. This could cause problems for values > 127.", e);
            return (new VarInt(intValue)).encode();
        }
    }

    public static byte[] transformLongToVarIntByteArray(long longValue) {
        try {
            long value = longValue;

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            DataOutput out = new DataOutputStream(byteArrayOutputStream);

            while ((value & 0xFFFFFFFFFFFFFF80L) != 0L) {
                out.writeByte(((int) value & 0x7F) | 0x80);
                value >>>= 7;
            }

            out.writeByte((int) value & 0x7F);

            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            Log.e (TAG,"Could not transform the given long value into its VarInt representation - "
                    + "Using BitcoinJ as Fallback. This could cause problems for values > 127.", e);
            return (new VarInt(longValue)).encode();
        }
    }



    private void performAnotherAPIRequest(JSONObject dynamicProps) {
        try {
            // Extract the necessary values from the response
            int refBlockNum = dynamicProps.getInt("head_block_number") & 0xffff;
            String refBlockId = dynamicProps.getString("head_block_id");


            // Convert refBlockId from hex to UInt32
            byte[] refBlockIdBytes = hexStringToByteArray(refBlockId);

            // Create a ByteBuffer from the byte array
            ByteBuffer buffer = ByteBuffer.wrap(refBlockIdBytes);

            // Set the byte order of the ByteBuffer to little endian
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Read the 32-bit unsigned integer at position 4 in the ByteBuffer
            int refBlockPrefix = buffer.getInt(4);


                                    /*int refBlockPrefix = ((refBlockIdBytes[4] & 0xFF) |
                                            ((refBlockIdBytes[5] & 0xFF) << 8) |
                                            ((refBlockIdBytes[6] & 0xFF) << 16) |
                                            ((refBlockIdBytes[7] & 0xFF) << 24));*/

            // Step 3: Create the transaction JSON
            JSONObject transaction = createTransactionJson(refBlockNum, refBlockPrefix);

            // Step 4: Serialize the transaction
            byte[] serializedTransaction = serializeTransaction(transaction);

            Log.d(TAG, "Serialized transaction: " + byteArrayToHexString(serializedTransaction));

            byte[] chainIdBytes = hexStringToByteArray(chainId);
            byte[] transactionData = ByteBuffer.allocate(4 + serializedTransaction.length)
                    .put(chainIdBytes)
                    .put(serializedTransaction) //crash: BufferOverflowException
                    .array();

            // Create digest
            byte[] digest = createDigest(transactionData);

            byte[] privateKey = getPrivateKey(ctx.getString(R.string.sample_p_key));

            // Sign the transaction
            byte[] signature = signTransaction(digest, privateKey);

            // Convert the signature to a string
            String stringSignature = byteArrayToHexString(signature);

            // Include the string signature in the transaction object
            includeSignatureInTransaction(stringSignature);

            // Check if the signature is canonical
            boolean isCanonical = isCanonicalSignature(signature);

            System.out.println("Digest: " + byteArrayToHexString(digest));
            System.out.println("Signature is canonical: " + isCanonical);

            // Optional: Do something with the serialized transaction
        }catch(Exception ex){
            ex.printStackTrace();
        }

    }



    //to handle multiple successive calls
    public interface APIResponseListener {
        //void onResponse(byte[] serializedTransaction);
        void onError(String errorMessage);

        void onResponse(JSONObject dynamicProps);
    }

    private interface SignatureObject {

    }

    public enum PrivateKeyType {
        /** The owner key type */
        OWNER,
        /** The active key type */
        ACTIVE,
        /** The memo key type */
        MEMO,
        /** The posting key type */
        POSTING,
        /**
         * The 'OTHER' key type is no real key type - It is only used to indicate
         * that an authority needs to be provided.
         */
        OTHER
    }

    public enum OperationType {
        /*
        ref: https://gitlab.syncad.com/hive/hive/-/blob/master/libraries/protocol/include/hive/protocol/operations.hpp
         */

        VOTE_OPERATION(0),
        COMMENT_OPERATION(1),
        TRANSFER_OPERATION(2),
        TRANSFER_TO_VESTING_OPERATION(3),
        WITHDRAW_VESTING_OPERATION(4),
        LIMIT_ORDER_CREATE_OPERATION(5),
        LIMIT_ORDER_CANCEL_OPERATION(6),
        FEED_PUBLISH_OPERATION(7),
        CONVERT_OPERATION(8),
        ACCOUNT_CREATE_OPERATION(9),
        ACCOUNT_UPDATE_OPERATION(10),
        WITNESS_UPDATE_OPERATION(11),
        ACCOUNT_WITNESS_VOTE_OPERATION(12),
        ACCOUNT_WITNESS_PROXY_OPERATION(13),
        POW_OPERATION(14),
        CUSTOM_OPERATION(15),
        WITNESS_BLOCK_APPROVE_OPERATION(16),
        DELETE_COMMENT_OPERATION(17),
        CUSTOM_JSON_OPERATION(18),
        COMMENT_OPTIONS_OPERATION(19),
        SET_WITHDRAW_VESTING_ROUTE_OPERATION(20),
        LIMIT_ORDER_CREATE2_OPERATION(21),
        CLAIM_ACCOUNT_OPERATION(22),
        CREATE_CLAIMED_ACCOUNT_OPERATION(23),
        REQUEST_ACCOUNT_RECOVERY_OPERATION(24),
        RECOVER_ACCOUNT_OPERATION(25),
        CHANGE_RECOVERY_ACCOUNT_OPERATION(26),
        ESCROW_TRANSFER_OPERATION(27),
        ESCROW_DISPUTE_OPERATION(28),
        ESCROW_RELEASE_OPERATION(29),
        POW2_OPERATION(30),
        ESCROW_APPROVE_OPERATION(31),
        TRANSFER_TO_SAVINGS_OPERATION(32),
        TRANSFER_FROM_SAVINGS_OPERATION(33),
        CANCEL_TRANSFER_FROM_SAVINGS_OPERATION(34),
        CUSTOM_BINARY_OPERATION(35),
        DECLINE_VOTING_RIGHTS_OPERATION(36),
        RESET_ACCOUNT_OPERATION(37),
        SET_RESET_ACCOUNT_OPERATION(38),
        CLAIM_REWARD_BALANCE_OPERATION(39),
        DELEGATE_VESTING_SHARES_OPERATION(40),
        ACCOUNT_CREATE_WITH_DELEGATION_OPERATION(41),
        WITNESS_SET_PROPERTIES_OPERATION(42),
        ACCOUNT_UPDATE2_OPERATION(43),
        CREATE_PROPOSAL_OPERATION(44),
        UPDATE_PROPOSAL_VOTES_OPERATION(45),
        REMOVE_PROPOSAL_OPERATION(46),
        UPDATE_PROPOSAL_OPERATION(47),
        COLLATERALIZED_CONVERT_OPERATION(48),
        RECURRENT_TRANSFER_OPERATION(49);

        private int opId;


        private OperationType(int opId) {
            this.opId = opId;
        }


        public int getOpId() {
            return opId;
        }
        }
}
