package ml.docilealligator.infinityforreddit;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.ArrayList;
import java.util.Map;

/**
 * Created by alex on 3/12/18.
 */

class BestPostPaginationScrollListener extends RecyclerView.OnScrollListener {
    private Context mContext;
    private LinearLayoutManager mLayoutManager;
    private BestPostRecyclerViewAdapter mAdapter;
    private ArrayList<BestPostData> mBestPostData;
    private PaginationSynchronizer mPaginationSynchronizer;
    private PaginationRetryNotifier mPaginationRetryNotifier;
    private LastItemSynchronizer mLastItemSynchronizer;
    private PaginationRequestQueueSynchronizer mPaginationRequestQueueSynchronizer;

    private boolean isLoading;
    private boolean loadSuccess;
    private String mLastItem;
    private RequestQueue mRequestQueue;
    private RequestQueue mAcquireAccessTokenRequestQueue;

    BestPostPaginationScrollListener(Context context, LinearLayoutManager layoutManager, BestPostRecyclerViewAdapter adapter, String lastItem, ArrayList<BestPostData> bestPostData, PaginationSynchronizer paginationSynchronizer,
                                     RequestQueue acquireAccessTokenRequestQueue, boolean isLoading, boolean loadSuccess) {
        if(context != null) {
            this.mContext = context;
            this.mLayoutManager = layoutManager;
            this.mAdapter = adapter;
            this.mLastItem = lastItem;
            this.mBestPostData = bestPostData;
            this.mPaginationSynchronizer = paginationSynchronizer;
            this.mAcquireAccessTokenRequestQueue = acquireAccessTokenRequestQueue;
            this.isLoading = isLoading;
            this.loadSuccess = loadSuccess;

            mRequestQueue = Volley.newRequestQueue(mContext);
            mAcquireAccessTokenRequestQueue = Volley.newRequestQueue(mContext);
            mPaginationRetryNotifier = new PaginationRetryNotifier() {
                @Override
                public void retry() {
                    fetchBestPost(1);
                }
            };
            mPaginationSynchronizer.setPaginationRetryNotifier(mPaginationRetryNotifier);
            mLastItemSynchronizer = mPaginationSynchronizer.getLastItemSynchronizer();
            mPaginationRequestQueueSynchronizer = mPaginationSynchronizer.getPaginationRequestQueueSynchronizer();
            mPaginationRequestQueueSynchronizer.passQueue(mRequestQueue);
        }
    }

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
        if(!isLoading && loadSuccess) {
            int visibleItemCount = mLayoutManager.getChildCount();
            int totalItemCount = mLayoutManager.getItemCount();
            int firstVisibleItemPosition = mLayoutManager.findFirstVisibleItemPosition();

            if((visibleItemCount + firstVisibleItemPosition >= totalItemCount) && firstVisibleItemPosition >= 0) {
                fetchBestPost(1);
            }
        }
    }


    private void fetchBestPost(final int refreshTime) {
        if(refreshTime < 0) {
            loadFailed();
            return;
        }

        isLoading = true;
        loadSuccess = false;
        mPaginationSynchronizer.setLoading(true);

        StringRequest bestPostRequest = new StringRequest(Request.Method.GET, RedditUtils.OAUTH_API_BASE_URI + RedditUtils.BEST_POST_SUFFIX + "&" + RedditUtils.AFTER_KEY + "=" + mLastItem, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("response", response);
                clipboard.setPrimaryClip(clip);
                new ParseBestPost(mContext, new ParseBestPost.ParseBestPostListener() {
                    @Override
                    public void onParseBestPostSuccess(ArrayList<BestPostData> bestPostData, String lastItem) {
                        mAdapter.notifyDataSetChanged();
                        mLastItem = lastItem;
                        mLastItemSynchronizer.lastItemChanged(mLastItem);

                        isLoading = false;
                        loadSuccess = true;
                        mPaginationSynchronizer.setLoading(false);
                        mPaginationSynchronizer.setLoadingState(true);
                    }

                    @Override
                    public void onParseBestPostFail() {
                        Toast.makeText(mContext, "Error parsing data", Toast.LENGTH_SHORT).show();
                        Log.i("Best post", "Error parsing data");
                        loadFailed();
                    }
                }).parseBestPost(response, mBestPostData);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                if (error instanceof AuthFailureError) {
                    //Access token expired
                    new AcquireAccessToken(mContext).refreshAccessToken(mAcquireAccessTokenRequestQueue,
                            new AcquireAccessToken.AcquireAccessTokenListener() {
                                @Override
                                public void onAcquireAccessTokenSuccess() {
                                    fetchBestPost(refreshTime - 1);
                                }

                                @Override
                                public void onAcquireAccessTokenFail() {
                                }
                            });
                } else {
                    Toast.makeText(mContext, "Error getting best post", Toast.LENGTH_SHORT).show();
                    Log.i("best post", error.toString());
                    loadFailed();
                }
            }
        }) {
            @Override
            public Map<String, String> getHeaders() {
                String accessToken = mContext.getSharedPreferences(SharedPreferencesUtils.AUTH_CODE_FILE_KEY, Context.MODE_PRIVATE).getString(SharedPreferencesUtils.ACCESS_TOKEN_KEY, "");
                return RedditUtils.getOAuthHeader(accessToken);
            }
        };
        bestPostRequest.setTag(BestPostPaginationScrollListener.class);
        mRequestQueue.add(bestPostRequest);
    }

    private void loadFailed() {
        isLoading = false;
        loadSuccess = false;
        mPaginationSynchronizer.setLoading(false);
        mPaginationSynchronizer.setLoadingState(false);
    }
}