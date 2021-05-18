package com.didimstory.hotelselfproject.repository;

import android.support.annotation.NonNull;
import android.util.Log;

import com.android.volley.Response;
import com.hotelnjoy.BuildConfig;
import com.hotelnjoy.util.HotelNJoyUtil;
import com.hotelnjoy.util.http.API;
import com.hotelnjoy.util.http.BaseModel;
import com.hotelnjoy.view.member.login.LoginInfo;
import com.matei.mateinetworklibrary.AES256Cipher;
import com.matei.mateinetworklibrary.HttpNetworkConnector;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.ObservableEmitter;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.BehaviorSubject;

/**
 * ViewModel -> Repository 동신을위한 최상위 Repository
 *
 * @param <T>
 */
public abstract class BaseRepository<T extends BaseModel> implements com.hotelnjoy.repository.getResponseInterFace<T> {
    private HttpNetworkConnector<T> mHttpNetworkConnector;
    private boolean isVolleyNetwork = false;
    private BehaviorSubject<Boolean> nowUseNetworkSubject;


    protected enum TYPE {
        OVERSEAS_HOTEL("해외 호텔", "hotelnjoy"), HOTEL("호텔", "hotelnjoy"), GAJAGO("액티비티", "gajago"), TABLE("다이닝", "tablenjoy");
        final private String name;
        final private String code;

        TYPE(String name, String code) {
            this.name = name;
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public String getCode() {
            return code;
        }
    }

    public BaseRepository() {
        if (nowUseNetworkSubject == null) {
            nowUseNetworkSubject = BehaviorSubject.createDefault(false);
        }
        WeakReference<CompositeDisposable> mDisposable = new WeakReference<>(new CompositeDisposable());
        mDisposable.get().add(nowUseNetworkSubject
                .delay(1, TimeUnit.SECONDS)
                .subscribe(
                        aBoolean -> isVolleyNetwork = aBoolean, throwable -> {
                        }
                ));


    }

    protected enum HTTP_TYPE {
        VOLLEY, RETROFIT
    }


    protected String apiEncryption(HTTP_TYPE type, String value) {
        String apiURL = "";
        if (!BuildConfig.DEBUG) {
            try {
                if (type == HTTP_TYPE.RETROFIT) {
                    apiURL = AES256Cipher.encodeAES(value).trim();
                } else {
                    apiURL = URLEncoder.encode(AES256Cipher.encodeAES(value).trim(), "UTF-8");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            apiURL = value;
        }

        return apiURL.trim();
    }


    protected String apiDecodeAES(String value) {
        String apiURL = "";
        if (!BuildConfig.DEBUG) {
            try {
                apiURL = URLEncoder.encode(AES256Cipher.decodeAES(value).trim(), "UTF-8");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            apiURL = value;
        }

        return apiURL.trim();
    }

    protected String isTest() {
        return BuildConfig.DEBUG ? "Y" : "N";
    }

    private String isDebug() {
        return BuildConfig.DEBUG ? "Y" : "N";
    }

    /**
     * 기본적인 커리를 완성하여 리턴하며 추가적으로 Map 형태의 Values 를 받아 세팅한다.
     * 기본적인 값 agentCode / device / appVersion / v_debug / test 값
     *
     * @param type   Query 형태 타잎을 받는다 호텔 / 가자고 / 다이닝
     * @param apiKey //apiKey를 받아 세팅한다.
     * @return 기본 QueryMap + Values Map
     */
    protected Map<String, String> getQuery(HTTP_TYPE httpType, TYPE type, String apiKey) {
        final Map<String, String> map = getDefaultMap(httpType, type, apiKey);


        //추가적으로 자식에서 구현받은 map key Values 룰 저장한다. key 값으로 받아온 values 값이 빈경우 넣지 않는ㄴ다.
        for (String key : queryValuesMap().keySet()) {
            if (queryValuesMap().get(key) != null) {
                if (!queryValuesMap().get(key).isEmpty()) {
                    map.put(key, apiEncryption(httpType, queryValuesMap().get(key)));
                }
            }

        }
        return removeLonAndLat(map);
    }

    @NonNull
    private Map<String, String> getDefaultMap(HTTP_TYPE httpType, TYPE type, String apiKey) {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("apiKey", apiEncryption(httpType, apiKey));
        map.put("agentCode", apiEncryption(httpType, "hotelnjoy"));
        map.put("device", apiEncryption(httpType, !type.equals(TYPE.GAJAGO) ? "AM" : "A"));
        map.put("appVersion", HotelNJoyUtil.sVersion);
//        map.put("v_debug", apiEncryption(isDebug()));
        if (!LoginInfo.getInstance().getmLoginUserID().equals("")) {
            map.put(!type.equals(TYPE.HOTEL) && !type.equals(TYPE.OVERSEAS_HOTEL) ? "user_id" : "userID", apiEncryption(httpType, LoginInfo.getInstance().getmLoginUserID()));
            if (type.equals(TYPE.OVERSEAS_HOTEL)) {
                map.put("userKind", apiEncryption(httpType, LoginInfo.getInstance().getmLoginUserKind()));
                map.put("userSeq", apiEncryption(httpType, LoginInfo.getInstance().getmLoginUserSeq()));
            }
        }

        if (!type.equals(TYPE.HOTEL) && !type.equals(TYPE.OVERSEAS_HOTEL)) {
            map.put("agent", apiEncryption(httpType, getAgent(type)));
        }
        if (type.equals(TYPE.TABLE) || type.equals(TYPE.HOTEL) || type.equals(TYPE.OVERSEAS_HOTEL)) {
            map.put("lang", apiEncryption(httpType, "kor"));
        }
        map.put("test", isTest());
        return map;
    }

    protected Map<String, String> mapToString(String[] values, String key) {
        HashMap<String, String> strBuilder = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            strBuilder.put(key + "[" + i + "]", values[i]);
        }
        return strBuilder;
    }

    protected Map<String, String> getQuery(HTTP_TYPE httpType, TYPE type, String apiKey, Map<String, String> valuesMap) {
        final Map<String, String> map = getDefaultMap(httpType, type, apiKey);

        //추가적으로 받은 map key Values 룰 저장한다. key 값으로 받아온 values 값이 빈경우 넣지 않는ㄴ다.
        for (String key : valuesMap.keySet()) {
            try {
                if (!valuesMap.get(key).isEmpty()) {
                    map.put(key, apiEncryption(httpType, valuesMap.get(key)));
                }
            } catch (Exception ignored) {
            }
        }
        return removeLonAndLat(map);
    }

    private Map<String, String> removeLonAndLat(Map<String, String> map) {
        if (map.get("longitude") != null || map.get("latitude") != null) {
            if (map.get("user_id") != null) {
                map.remove("user_id");
            }
            if (map.get("userID") != null) {
                map.remove("userID");
            }
            if (map.get("userKind") != null) {
                map.remove("userKind");
            }
            if (map.get("userSeq") != null) {
                map.remove("userSeq");
            }
        }
        return map;
    }

    protected String getMakeUrlForVolley(final Map<String, String> map) {
        /*API.API_URL*/
        final StringBuilder url = new StringBuilder(API.API_BASE + "send.php?apiKey=");

        url.append(map.get("apiKey"));
        map.remove("apiKey");
        for (String key : map.keySet()) {
            url.append("&").append(key).append("=").append(map.get(key));
        }
//        Log.e("getMakeUrlForVolley", url.toString());
        return url.toString();
    }

    private String getAgent(TYPE type) {
        return type.code;
    }


    protected boolean isVolleyNetwork() {
        nowUseNetworkSubject.onNext(!isVolleyNetwork);
        return isVolleyNetwork;
    }

    protected void setVolleyNetwork(boolean volleyNetwork) {
        isVolleyNetwork = volleyNetwork;
        nowUseNetworkSubject.onNext(!isVolleyNetwork);
    }

    protected Map<String, String> getValuesMap() {
        return new LinkedHashMap<>();
    }


    private synchronized void requestQue(String url, Class<T> clazz, Response.Listener<T> responseListener, Response.ErrorListener errorListener) {
        mHttpNetworkConnector = new HttpNetworkConnector<>(clazz.getSimpleName(), responseListener, errorListener);
        mHttpNetworkConnector.requestQue(url, clazz, null, url, responseListener, errorListener);
    }


    protected synchronized void requestQue(ObservableEmitter<T> emitter, String url, Class<T> clazz) {
        cancelRequestQue(url);
        requestQue(url, clazz, response -> {
            if (!emitter.isDisposed()) {
                emitter.onNext(response);
                emitter.onComplete();
            }

        }, error -> {
            final String errorMsg = error != null ? error.toString() : "Volley Error";

            Log.e("BaseRepository", "errorMsg =" + errorMsg);
            try {
                if (!emitter.isDisposed()) {
                    emitter.onError(new IOException(errorMsg));
                }

            } catch (Exception ignored) {

            } finally {
//                setVolleyNetwork(true);
            }
        });
    }

    private void cancelRequestQue(String tag) {
        if (mHttpNetworkConnector != null) {
            mHttpNetworkConnector.cancelRequestQue(tag);
        }
    }


}
