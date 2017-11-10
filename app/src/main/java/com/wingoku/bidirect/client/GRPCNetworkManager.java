package com.wingoku.bidirect.client;

import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.wingoku.bidirect.Request;
import com.wingoku.bidirect.Response;
import com.wingoku.bidirect.WingokuServiceGrpc;
import com.wingoku.bidirect.hellogrpc.R;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import butterknife.OnClick;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Umer on 10/11/2017.
 */

public class GRPCNetworkManager {
    private ManagedChannel mChannel;
    private StreamObserver<Request> mRequestStreamObserver;
    private WingokuServiceGrpc.WingokuServiceStub mAsyncStub;
    private long connectionRequestTime;// in millisecond
    private long connectionEstablishedTime; // in millisecond
    private ArrayList<Long> timeDifferenceList;
    private String mIpAddress = null;
    private int mPortNumber = -1;
    private boolean sentConnectedCallbackAlready, sentDisconnectedCallbackAlready;

    public enum ConnectionStatus {
        CONNECTED,
        DISCONNECTED,
        TERMINATE_POLLING
    }
    private ConnectionStatus mPreviousConnectionStatus;
    private ConnectivityState mPreviousConnectivityState;

    public void createNewChannelForClient(String ipAddress, int portNumber) {
        mIpAddress = (mIpAddress == null)? ipAddress:mIpAddress;
        mPortNumber = (mPortNumber == -1)? portNumber:mPortNumber;

        //Log.e("MainActivityFragment", "createNewChannelForClient");
//        if(mChannel == null)
        {
            connectionRequestTime = System.currentTimeMillis();
            mChannel = ManagedChannelBuilder.forAddress(ipAddress, portNumber).usePlaintext(true).build();
            Log.i("wingoku", "creating new mChannel: "+mChannel+",   time: "+ System.currentTimeMillis());
//            startListenerToListenForChannelConnectionStateBecomingReady();
            createStubAndObserver();
        }
    }

    /**
     * MUST RUN THIS METHOD ON BACKGROUND THREAD IN RXJAVA!!
     * NOTE:
     * ONCE THE CONNECTION STATE IS READY. THIS OBSERVABLE I-E; SINGLE WILL DIE.
     * TO LEARN ABOUT THE CLIENT DISCONNECTION FROM THE SERVER WE MUST RELY ON THE GRPC CHANNEL'S STREAM OBSERVER'S ONERROR AND ONCOMPLETE CALLBACKS
     */
    public Observable<GRPCNetworkManager.ConnectionStatus> getObservableForChannelConnectionAndDisconnectionState() {
        /*Single.create(new SingleOnSubscribe<Boolean>() {
            @Override
            public void subscribe(SingleEmitter<Boolean> singleEmitter) throws Exception {
                try {
                    while (mChannel != null) {
                        ConnectivityState state = mChannel.getState(true);
                        if (ConnectivityState.READY == ConnectivityState.valueOf(state.name())) {
                            singleEmitter.onSuccess(true);
                            continue;
                        }

                        singleEmitter.onSuccess(false);
                    }
                }
                catch (Exception e) {
                    //Log.e("wingoku", e.getLocalizedMessage());
                }
            }
        });*/
        /*
        ConnectivityState state = mChannel.getState(true);
                        // if the new connectivity status is READY state and previous connectivity status of the mChannel wasn't ConnectivityStatus.Ready then and only then send
                        // out an event that the connection is successfully established. We don't want to keep spamming the listener of connectivity status to keep getting events while
                        // the connectivity status of the mChannel hasn't changed
                        if (ConnectivityState.READY == ConnectivityState.valueOf(state.name()) && PREVIOUS_STATUS_FOR_CHANNEL_CONNECTION != ConnectivityState.READY) {
                            //emit connected status
                            PREVIOUS_STATUS_FOR_CHANNEL_CONNECTION = ConnectivityState.READY;
                            return true;
                        }

                        PREVIOUS_STATUS_FOR_CHANNEL_CONNECTION = ConnectivityState.valueOf(state.name());
                        return false;
         */
        //Log.i("wingoku", "inside observer method");
        Log.i("wingoku", "getObservableForChannelConnectionAndDisconnectionState(): time: "+ System.currentTimeMillis());
        return Observable.interval(100, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.computation())
                .flatMap(new Function<Long, ObservableSource<ConnectionStatus>>() {
                    ManagedChannel channel = mChannel;
                    WingokuServiceGrpc.WingokuServiceStub asyncStub = mAsyncStub;
                    StreamObserver<Request> requestStreamObserver = mRequestStreamObserver;

                    @Override
                    public ObservableSource<ConnectionStatus> apply(Long aLong) throws Exception {

                        if(channel == null)
                            return Observable.just(ConnectionStatus.TERMINATE_POLLING);
                        // if the new connectivity status is READY state and previous connectivity status of the mChannel wasn't ConnectivityStatus.Ready then and only then send
                        // out an event that the connection is successfully established. We don't want to keep spamming the listener of connectivity status to keep getting events while
                        // the connectivity status of the mChannel hasn't changed
//                        //Log.i("wingoku", "mChannel == "+ mChannel);
                        ConnectivityState currentState = ConnectivityState.valueOf(mChannel.getState(true).name());
//                        //Log.i("wingoku", "flatmap currentNetworkState: "+currentState.name());
//                        Log.i("wingoku", "flatMap::apply()  networkStatus: "+ currentState.name());
                        if(currentState == ConnectivityState.READY /*&& !sentConnectedCallbackAlready*/) {
                            /*sentConnectedCallbackAlready = true;
                            sentDisconnectedCallbackAlready = false;*/
                            return Observable.just(ConnectionStatus.CONNECTED);
                        }

//                        clearChannelAndStubsAndStreamObserver(channel, asyncStub, requestStreamObserver);
                       /* if((currentState == ConnectivityState.IDLE) ||
                                (currentState == ConnectivityState.CONNECTING) ||
                                (currentState == ConnectivityState.TRANSIENT_FAILURE) ||
                                (currentState == ConnectivityState.SHUTDOWN) *//*&& !sentDisconnectedCallbackAlready*//*) {
                            sentConnectedCallbackAlready = false;
                            sentDisconnectedCallbackAlready = true;
                            return Observable.just(ConnectionStatus.DISCONNECTED);
                        }*/
                        return Observable.just(ConnectionStatus.DISCONNECTED);
                    }
                })
                .filter(new Predicate<ConnectionStatus>() {
                    @Override
                    public boolean test(ConnectionStatus state) throws Exception {
                        if(mPreviousConnectionStatus != ConnectionStatus.valueOf(state.name())) {
                            //Log.e("wingokuBenchmark", "Inside manager: state: "+ state.name());
                            mPreviousConnectionStatus = state;
                            return true;
                        }
                        return false;
//                        ConnectivityState currentState = ConnectivityState.valueOf(state.name());

//                        if ((ConnectivityState.READY == currentState || ConnectivityState.TRANSIENT_FAILURE == currentState || ConnectivityState. == currentState)
//                                && PREVIOUS_STATUS_FOR_CHANNEL_CONNECTION != ConnectivityState.READY) {
//                            //emit connected status
//                            PREVIOUS_STATUS_FOR_CHANNEL_CONNECTION = ConnectivityState.READY;
//                            return true;
//                        }
//
//                        PREVIOUS_STATUS_FOR_CHANNEL_CONNECTION = ConnectivityState.valueOf(state.name());
//                        return false;

                        // if the new connectivity status is READY state and previous connectivity status of the mChannel wasn't ConnectivityStatus.Ready then and only then send
                        // out an event that the connection is successfully established. We don't want to keep spamming the listener of connectivity status to keep getting events while
                        // the connectivity status of the mChannel hasn't changed
//                       if(currentState == ConnectivityState.READY && !sentConnectedCallbackAlready) {
//                           sentConnectedCallbackAlready = true;
//                           sentDisconnectedCallbackAlready = false;
//                           return true;
//                       }
//
//                        if((currentState == ConnectivityState.IDLE) ||
//                                (currentState == ConnectivityState.CONNECTING) ||
//                                (currentState == ConnectivityState.TRANSIENT_FAILURE) ||
//                                (currentState == ConnectivityState.SHUTDOWN) && !sentDisconnectedCallbackAlready) {
//                            sentConnectedCallbackAlready = false;
//                            sentDisconnectedCallbackAlready = true;
//                            return true;
//                        }
//
//                        return false;
                    }
                });
    }

    private void createStubAndObserver() {
        mAsyncStub = WingokuServiceGrpc.newStub(mChannel);
        mRequestStreamObserver = mAsyncStub.messages(new StreamObserver<Response>() {
            @Override
            public void onNext(Response response) { //similar to onNext on java side
                //Log.e("MainActivityFragment", "Client onNext");
                //Log.e("MainActivityFragment", "Response from server: (" + response.getResponseMessage() + ") System.currentTimeMillis: " + System.currentTimeMillis());
            }

            @Override
            public void onError(Throwable throwable) {
                //Log.e("MainActivityFragment", "Client onError");
                throwable.printStackTrace();
            }

            @Override
            public void onCompleted() {
                //Log.e("MainActivityFragment", "Client OnComplete");
            }
        });
    }

    public void sendMessageToServer() {
        if(mAsyncStub == null && mRequestStreamObserver == null) {
            createStubAndObserver();
        }
        mRequestStreamObserver.onNext(Request.newBuilder().setRequestMessage("message from client").build());
    }

    public void reconnectToServer() {
        mRequestStreamObserver.onCompleted();
        if(mChannel != null) {
            createStubAndObserver();
        }
    }

    public synchronized void disconnectFromServer() {
        Log.v("wingoku", "disconnectFromServer()");
        mRequestStreamObserver.onCompleted();
        try {
            mChannel.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mChannel.shutdown();
    }

    private synchronized void clearChannelAndStubsAndStreamObserver(ManagedChannel channel, WingokuServiceGrpc.WingokuServiceStub asyncStub, StreamObserver<Request> requestStreamObserver) {
        Log.e("wingoku", "clearChannelAndStubsAndStreamObserver() for mChannel: "+ mChannel + " time: "+ System.currentTimeMillis());
        asyncStub = null;
        requestStreamObserver = null;
        //Log.v("wingoku", "disconnectFromServer: mChannel == "+mChannel);
        channel = null;
    }

    public void reOpenChannelFromServerBenchmark() {
        createNewChannelForClient(mIpAddress, mPortNumber);
    }

    public ManagedChannel getChannel() {
        return mChannel;
    }

    /*public void benchmarkGRPCConnection(String benchmarkCycles) {
        numberOfTimesToRunBenchmark = Integer.valueOf(benchmarkCycles);
        if(numberOfTimesToRunBenchmark <= 0) {
            mLogText.setText("Benchmark Ran 0 Times");
            return;
        }

        mTimesBenchmarkRanSofar = 0;
        Timer timerObj = new Timer();
        timerTaskObj = new TimerTask() {
            public void run() {
                if(numberOfTimesToRunBenchmark<mTimesBenchmarkRanSofar)
                    stopBenchmark();

                mTimesBenchmarkRanSofar++;
                //Log.e("wingokuBenchMark", "Benchmark cycles: "+ mTimesBenchmarkRanSofar);
                //perform your action here
                if(mChannel != null)
                    disconnectFromServerBenchmark();
                reOpenChannelFromServerBenchmark();
            }
        };
        timerObj.scheduleAtFixedRate(timerTaskObj, 0, 1000);
    }


    private void stopBenchmark() {
        timerTaskObj.cancel();

        long sum=0;
        for(long diff : timeDifferenceList) {
            sum+=diff;
        }
        float averageTime = (sum*1.0f/timeDifferenceList.size()*1.0f);
        benchResult = "AverageTime took to establish "+timeDifferenceList.size()+" connections with server: "+ averageTime;

       *//* getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLogText.setText(benchResult);
            }
        });*//*
    }
*/
}
