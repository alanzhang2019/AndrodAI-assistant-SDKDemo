package com.demo.map;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;


import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItemV2;
import com.amap.api.services.core.ServiceSettings;
import com.amap.api.services.poisearch.PoiResultV2;
import com.amap.api.services.poisearch.PoiSearchV2;

import java.util.ArrayList;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MiniMap implements PoiSearchV2.OnPoiSearchListener{
    private final Context context;
    private LatLonPoint currentLocation;
    private int searchRadius;
    private final String LOG_TAG = "MiniMap";

    public MiniMap(Context context){
        this.context = context;
    }

    /**
     *  初始化 PS: 创建之后必须初始化
     * @param latitude 纬度
     * @param longitude 经度
     * @param searchRadius 搜索半径
     */
    protected void initializeMap(double latitude, double longitude, int searchRadius){
        // 设置隐私合规
        updatePrivacyShow(context);
        updatePrivacyAgree(context);
        // 设置一个默认位置和半径
        updateCurrentLocation(latitude, longitude);
        updateSearchRadius(searchRadius);
    }

    /**
     * 操作前必做
     * @param context
     */
    private void updatePrivacyShow(Context context) {
        ServiceSettings.updatePrivacyShow(context, true, true);
    }

    /**
     * 操作前必做
     * @param context
     */
    private void updatePrivacyAgree(Context context) {
        ServiceSettings.updatePrivacyAgree(context, true);
    }

    /**
     * 更新当前位置
     * @param latitude 纬度
     * @param longitude 经度
     */
    protected void updateCurrentLocation(double latitude, double longitude) {
        currentLocation = new LatLonPoint(latitude, longitude);
    }

    /**
     * 更新搜索半径 单位米
     * @param radius 半径
     */
    protected void updateSearchRadius(int radius) {
        this.searchRadius = radius;
    }

//    protected String searchPOI_bak(String keyword, int searchNum){
//        final String[] result = {""};
//        try {
//            PoiSearchV2.Query query = new PoiSearchV2.Query(keyword, "", "");
//            query.setPageSize(searchNum);
//            query.setPageNum(1);
//
//            PoiSearchV2 poiSearch = new PoiSearchV2(context, query);
//            poiSearch.setOnPoiSearchListener(new PoiSearchV2.OnPoiSearchListener() {
//                @Override
//                public void onPoiSearched(PoiResultV2 poiResult, int errorCode) {
//                    String result = handleSearchResult(poiResult, errorCode, keyword, false, searchNum);
//                    Toast.makeText(context, "搜索结束", Toast.LENGTH_SHORT).show();
//                    // 使用Handler来确保在Toast显示后完成future
//                }
//
//                @Override
//                public void onPoiItemSearched(PoiItemV2 poiItemV2, int i) {
//                    // 处理单个POI项搜索结果（如果需要）
//                }
//            });
//            // 设置周边搜索的范围
//            if (currentLocation != null) {
//                PoiSearchV2.SearchBound searchBound = new PoiSearchV2.SearchBound(currentLocation, searchRadius);  // searchRadius米范围
//                poiSearch.setBound(searchBound);
//            }
//            poiSearch.searchPOIAsyn();
//        } catch (AMapException e) {
//            Log.e(LOG_TAG,e.getMessage());
//            return null;
//        }
//        return result[0];
//    }

    /**
     * 进行附近搜索，如果没有搜索结果转为全国搜索
     *
     * @param keyword   搜索关键词
     * @param searchNum 搜索条目
     * @return 搜索结果的JSON字符串
     */
    protected @NonNull Observable<Object> searchPOI(String keyword, int searchNum){
        return Observable.create(emitter -> {
            try {
                PoiSearchV2.Query query = new PoiSearchV2.Query(keyword, "", "");
                query.setPageSize(searchNum);
                query.setPageNum(1);

                PoiSearchV2 poiSearch = new PoiSearchV2(context, query);
                poiSearch.setOnPoiSearchListener(new PoiSearchV2.OnPoiSearchListener() {
                    @Override
                    public void onPoiSearched(PoiResultV2 poiResult, int errorCode) {
                        String result = handleSearchResult(poiResult, errorCode, keyword, false, searchNum);
                        AndroidSchedulers.mainThread().scheduleDirect(() ->
                                Toast.makeText(context, "搜索结束", Toast.LENGTH_SHORT).show()
                        );
                        emitter.onNext(result);
                        emitter.onComplete();
                    }

                    @Override
                    public void onPoiItemSearched(PoiItemV2 poiItemV2, int i) {
                        // 处理单个POI项搜索结果（如果需要）
                    }
                });

                // 设置周边搜索的范围
                if (currentLocation != null) {
                    PoiSearchV2.SearchBound searchBound = new PoiSearchV2.SearchBound(currentLocation, searchRadius);
                    poiSearch.setBound(searchBound);
                }
                poiSearch.searchPOIAsyn();

                // 如果观察者取消订阅，我们应该停止搜索
                emitter.setCancellable(() -> poiSearch.setOnPoiSearchListener(null));

            } catch (AMapException e) {
                Log.e(LOG_TAG, "Error in POI search", e);
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    /* 调用方式
    miniMap.searchPOI(location, 5)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                result -> {
                                    listView.setText((CharSequence) result);
                                },
                                error -> {
                                    Log.e("MainActivity", "Search failed", error);
                                }
                            );
     */

    /**
     * 全国搜索
     * @param keyword 搜索关键词
     * @param searchNum 搜索条目
//     * @return JSON字符串
     */
    protected String performNationwideSearch(String keyword, int searchNum) {
        final String[] result = {""};

        try {
            PoiSearchV2.Query query = new PoiSearchV2.Query(keyword, "", "");
            query.setPageSize(searchNum);
            query.setPageNum(1);

            PoiSearchV2 nationwideSearch = new PoiSearchV2(context, query);
            nationwideSearch.setOnPoiSearchListener(new PoiSearchV2.OnPoiSearchListener() {
                @Override
                public void onPoiSearched(PoiResultV2 poiResult, int errorCode) {
                    result[0] = handleSearchResult(poiResult, errorCode, keyword, true, searchNum);
                }

                @Override
                public void onPoiItemSearched(PoiItemV2 poiItem, int errorCode) {
                    // 处理单个POI项搜索结果（如果需要）
                }
            });

            nationwideSearch.searchPOIAsyn();
            Log.d(LOG_TAG, "正在进行全国搜索: " + keyword);
        } catch (AMapException e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "全国搜索初始化失败: " + e.getMessage());
            return null;
        }
        return result[0];
    }

    /**
     * 搜索结果的处理逻辑,如果搜索结果为空就转为全国搜索
     * @param poiResult 搜索结果
     * @param errorCode 返回码
     * @param keyword 搜索关键词
     * @param isNationwideSearch 防止循环搜索，当全国搜索搜不到之后就停止了
     * @param searchNum 搜索条目
     * @return Json字符串
     */
    protected String handleSearchResult(PoiResultV2 poiResult, int errorCode, String keyword, boolean isNationwideSearch, int searchNum) {
        if (errorCode == 1000) {
            if (poiResult != null && poiResult.getPois() != null && !poiResult.getPois().isEmpty()) {
                return formatSearchResults(poiResult.getPois());
            } else {
                if (!isNationwideSearch) {
                    Log.d(LOG_TAG, "周边搜索无结果，切换到全国搜索");
                    return performNationwideSearch(keyword, searchNum);
                } else {
                    Log.d(LOG_TAG, "全国搜索无结果");
                    return null;
                }
            }
        } else {
            Log.e(LOG_TAG, "搜索失败，错误码：" + errorCode);
            return null;
        }
    }

    /**
     * 格式化搜索结果为指定的JSON字符串格式
     * @param poiItems 搜索结果列表
     * @return 格式化后的JSON字符串
     */
    private String formatSearchResults(ArrayList<PoiItemV2> poiItems) {
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < poiItems.size(); i++) {
            PoiItemV2 item = poiItems.get(i);
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", i + 1);
                jsonObject.put("name", item.getTitle());
                jsonObject.put("address", item.getSnippet());
//                jsonObject.put("POI ID", item.getPoiId());
                LatLonPoint latLonPoint = item.getLatLonPoint();

                if (latLonPoint != null) {
                    jsonObject.put("latitude", latLonPoint.getLatitude());
                    jsonObject.put("longitude", latLonPoint.getLongitude());
                }
                jsonArray.put(jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        Toast.makeText(context, jsonArray.toString(), Toast.LENGTH_SHORT).show();
        Log.e("ss", jsonArray.toString());
        return jsonArray.toString();
    }

    @Override
    public void onPoiSearched(PoiResultV2 poiResultV2, int i) {

    }

    @Override
    public void onPoiItemSearched(PoiItemV2 poiItemV2, int i) {

    }

    /**
     * 释放资源并清理状态。
     * 在Activity或Fragment的onDestroy方法中调用此方法。
     */
    public void onDestroy() {
        // 清除位置信息
        this.currentLocation = null;
        // 重置搜索半径
        this.searchRadius = 0;
        Log.d(LOG_TAG, "MiniMap resources released");
    }
}
