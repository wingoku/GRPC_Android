package com.wingoku.bidirect.client;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.wingoku.bidirect.Request;
import com.wingoku.bidirect.Response;
import com.wingoku.bidirect.WingokuServiceGrpc;
import com.wingoku.bidirect.hellogrpc.R;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;


/**
 * This Fragment displays UI to handle communication with the bundled GRPC server.
 *
 * The user may enter server host/IP of the server and execute requests while examining the log
 * file.
 *
 * In a real implementation communication wouldn't be done here, but in a dedicated controller/job
 * class.
 */
public class MainActivityFragment extends Fragment {
    @BindView(R.id.main_edit_server_host)
    EditText mServerHostEditText;

    @BindView(R.id.main_edit_server_port)
    EditText mServerPortEditText;

    @BindView(R.id.connection_numbers_ET)
    EditText mConnectionTimesET;

    @BindView(R.id.main_text_log)
    TextView mLogText;

    @BindView(R.id.main_button_send_request)
    Button mOpenChannelButton;

    @BindView(R.id.button_disconnect)
    Button mDisconnectButton;

    @BindView(R.id.button_reconnect)
    Button mReconnectButton;

    @BindView(R.id.button_start_benchmark)
    Button mBenchmarkButton;

    //this list contains difference between connectionRequestTime and connectionEstablishedTime for N times
    private ArrayList<Long> timeDifferenceList;
    private CompositeDisposable mCompositeDisposable;

//    private ChannelImpl mChannel;
    private String mHost = "";
    private int mPort = -1;

    private GRPCNetworkManager mGRPCManager;
    private boolean isClientConnected = false;

    public MainActivityFragment () {
        //Log.e("wingoku", "constructor");
        timeDifferenceList = new ArrayList<>();
        mGRPCManager = new GRPCNetworkManager();
        mCompositeDisposable = new CompositeDisposable();
    }

    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState ) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        ButterKnife.bind(this, view);
//
//        makes log text view scroll automatically as new lines are added, just works in combination
//        with gravity:bottom

        mLogText.setMovementMethod(new ScrollingMovementMethod());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        //Log.e("wingoku", "onResume: benchCycles: "+ numberOfTimesToRunBenchmark);
    }

    @OnClick(R.id.main_text_log)
    public void onOutsideClicked(View view) {
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    TimerTask timerTaskObj;
    int mTimesBenchmarkRanSofar;
    int numberOfTimesToRunBenchmark;
    private boolean BENCHMARK_MODE = false;
    @OnClick(R.id.button_start_benchmark)
    public void benchmarkGRPCConnection() {
        numberOfTimesToRunBenchmark = Integer.valueOf(mConnectionTimesET.getText().toString());
        if(numberOfTimesToRunBenchmark <= 0) {
            mLogText.setText("Benchmark Ran 0 Times");
            return;
        }

        Log.v("wingoku", "benchmarkGRPCConnection() :: limitOfBenchmarkCycles: "+ numberOfTimesToRunBenchmark);
        //Log.v("wingokuBench", "number of times to run benchmark "+numberOfTimesToRunBenchmark);

        mTimesBenchmarkRanSofar = 0;
        timeDifferenceList.clear();

        disableButtonsAndEditText();

        BENCHMARK_MODE = true;
        sendRequestToServer();
    }

    String benchResult;
    private void stopBenchmark() {
        //Log.v("wingokuBench", "STOP BENCHMARK");
        Log.e("wingoku", "stop benchmark");
        BENCHMARK_MODE = false;
        long sum=0;
        for(long diff : timeDifferenceList) {
            sum+=diff;
        }
        float averageTime = (sum*1.0f/timeDifferenceList.size()*1.0f);
        benchResult = "AverageTime took to establish "+(timeDifferenceList.size()-1)+" connections with server: "+ averageTime;
        mLogText.setText(benchResult);
        enableButtonsAndEditText();
        mCompositeDisposable.dispose();
    }

    ManagedChannel channel;
    StreamObserver<Request> requestStreamObserver;
    WingokuServiceGrpc.WingokuServiceStub asyncStub;
    long connectionRequestTime;// in millisecond
    long connectionEstablishedTime; // in millisecond

    @OnClick(R.id.main_button_send_request)
    public void sendRequestToServer() {
//        new SendHelloTask().execute();
        //Log.e("MainActivityFragment", "Send request to server");
//        onPreExecute();
        Log.d("wingoku", "createChannel()::Fragment. BenchCycle: "+ mTimesBenchmarkRanSofar);
        mGRPCManager.createNewChannelForClient(mServerHostEditText.getText().toString(), Integer.valueOf(mServerPortEditText.getText().toString()));
        connectionRequestTime = System.currentTimeMillis();

        Log.e("wingoku", "type of observable taken from Manager: "+ "");
        Disposable disposable = mGRPCManager.getObservableForChannelConnectionAndDisconnectionState()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableObserver<GRPCNetworkManager.ConnectionStatus>() {
                    @Override
                    public void onNext(GRPCNetworkManager.ConnectionStatus connectionStatus) {
                        //Log.e("wingoku", "inside on next");
                        if(GRPCNetworkManager.ConnectionStatus.valueOf(connectionStatus.name()) == GRPCNetworkManager.ConnectionStatus.CONNECTED)
                            onClientConnectedToTheServer();
                        else
                        if(GRPCNetworkManager.ConnectionStatus.valueOf(connectionStatus.name()) == GRPCNetworkManager.ConnectionStatus.DISCONNECTED)
                            onClientDisconnectedToTheServer();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.i("wingoku", "subscribeWith():: onError");
                        //Log.e("wingoku", "inside on error: "+ throwable.getLocalizedMessage());
                        throwable.printStackTrace();
                        onClientDisconnectedToTheServer();
                    }

                    @Override
                    public void onComplete() {
                        //Log.e("wingoku", "inside on complete");
                        Log.v("wingoku", "subscribeWith():: onComplete");
                    }
                });

        mCompositeDisposable.add(disposable);
//            mGRPCManager.sendMessageToServer();
    }

   private void onClientConnectedToTheServer() {
        Log.i("wingoku", "onClientConnectedToTheServer() for channel: "+ mGRPCManager.getChannel());
       //Log.v("wingokuBenchmark", "onClientConnectedToTheServer for benchmarkCycle: "+mTimesBenchmarkRanSofar);
       isClientConnected = true;
       connectionEstablishedTime = System.currentTimeMillis();
//                        //Log.e("wingoku", "ConnectivityState = "+state.name()+"/CONNECTED!! at time(msec): "+ connectionEstablishedTime);
       long diff = connectionEstablishedTime - connectionRequestTime;
//       //Log.d("wingokuBenchMark", "connectionRequestTime: " + connectionRequestTime + " | connectionEstablishedTime: " + connectionEstablishedTime + " | difference: " + diff + " for benchmarkCycle: "+mTimesBenchmarkRanSofar);
       timeDifferenceList.add(diff);
       onClientConnectedToTheServerBenchmark();
   }

   private void onClientDisconnectedToTheServer() {
       Log.i("wingoku", "onClientDisconnectedToTheServer() for channel: "+ mGRPCManager.getChannel());
       isClientConnected = false;
       //Log.v("wingokuBenchmark", "onClientDisconnectedToTheServer for benchmarkCycle: "+mTimesBenchmarkRanSofar);
       onClientDisconnectedToTheServerBenchmark();
   }

   private void onClientConnectedToTheServerBenchmark() {
       disconnectFromServerBenchmark();
   }

   private void onClientDisconnectedToTheServerBenchmark() {
        Log.i("wingoku", "onClientDisconnectedToTheServerBenchmark() benchmark cycles: "+ mTimesBenchmarkRanSofar);
       //Log.v("wingokuBench", "numberOfTimesToRunBenchmark: "+ numberOfTimesToRunBenchmark+" mTimesBenchmarkRanSofar: "+mTimesBenchmarkRanSofar);
       if(mTimesBenchmarkRanSofar>=numberOfTimesToRunBenchmark) {
           stopBenchmark();
           return;
       }

       mTimesBenchmarkRanSofar++;
       reOpenChannelFromServerBenchmark();
   }

    @OnClick(R.id.button_reconnect)
    public void onReconnectToServer(View v) {
       mGRPCManager.reconnectToServer();
    }

    private void disconnectFromServerBenchmark() {
        //Log.v("wingoku", "disconnectFromServerBenchmark");
        Completable.timer(2, TimeUnit.SECONDS).subscribe(new Action() {
            @Override
            public void run() throws Exception {
                mGRPCManager.disconnectFromServer();
            }
        });
    }

    private void reOpenChannelFromServerBenchmark() {
        //Log.v("wingokuBench", "Reconnect to the server");
        if(!BENCHMARK_MODE)
            return;

        Completable.timer(2, TimeUnit.SECONDS).subscribe(new Action() {
            @Override
            public void run() throws Exception {
                sendRequestToServer();
            }
        });

    }

    @OnClick(R.id.button_disconnect)
    public void onDisconnectFromServer(View v) {
        //Log.e("wingoku", "onDisconnectFromSErver");
        mGRPCManager.disconnectFromServer();
        mDisconnectButton.setEnabled(false);
        mReconnectButton.setEnabled(true);
    }

    private void log ( String logMessage ) {
        mLogText.append("\n" + logMessage);
    }

    private void disableButtonsAndEditText() {
        mReconnectButton.setEnabled(false);
        mDisconnectButton.setEnabled(false);
        mOpenChannelButton.setEnabled(false);
        mBenchmarkButton.setEnabled(false);

        mServerPortEditText.setEnabled(false);
        mConnectionTimesET.setEnabled(false);
        mServerPortEditText.setEnabled(false);
    }

    private void enableButtonsAndEditText() {
        mReconnectButton.setEnabled(true);
        mDisconnectButton.setEnabled(true);
        mOpenChannelButton.setEnabled(true);
        mBenchmarkButton.setEnabled(true);

        mServerPortEditText.setEnabled(true);
        mConnectionTimesET.setEnabled(true);
        mServerPortEditText.setEnabled(true);
    }
}
