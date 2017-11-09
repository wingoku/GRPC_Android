package com.wingoku.grpc;

import android.app.Fragment;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.util.concurrent.UncheckedExecutionException;
import com.wingoku.grpc.hellogrpc.R;

import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.grpc.ChannelImpl;
import io.grpc.transport.okhttp.OkHttpChannelBuilder;


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

    @BindView(R.id.main_text_log)
    TextView mLogText;

    @BindView(R.id.main_button_send_request)
    Button mSendButton;

    private ChannelImpl mChannel;

    public MainActivityFragment () {
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
    public void onDestroyView () {
        super.onDestroyView();
        shutdownChannel();
    }

    @OnClick(R.id.main_button_send_request)
    public void sendRequestToServer () {
        new SendHelloTask().execute();
    }

    private void log ( String logMessage ) {
        mLogText.append("\n" + logMessage);
    }

    private void shutdownChannel () {
        if (mChannel != null) {
            try {
                mChannel.shutdown().awaitTerminated(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // FIXME this call seems fishy as it interrupts the main thread
                Thread.currentThread().interrupt();// NOT A GOOD PRACTICE BUT A TEMPORARY WORKAROUND
            }
        }
        mChannel = null;
    }

    private class SendHelloTask extends AsyncTask<Void, Void, String> {

        private String mHost = "";
        private int mPort = -1;

        @Override
        protected void onPreExecute () {
            mSendButton.setEnabled(false);

            String newHost = mServerHostEditText.getText().toString();
            if (!mHost.equals(newHost)) {
                mHost = newHost;
                shutdownChannel();
            }
            if (TextUtils.isEmpty(mHost)) {
                log("ERROR: empty host name!");
                cancel(true);
                return;
            }

            String portString = mServerPortEditText.getText().toString();
            if (TextUtils.isEmpty(portString)) {
                log("ERROR: empty port");
                cancel(true);
                return;
            }

            try {
                int newPort = Integer.parseInt(portString);
                if (mPort != newPort) {
                    mPort = newPort;
                    shutdownChannel();
                }
            } catch (NumberFormatException ex) {
                log("ERROR: invalid port");
                cancel(true);
                return;
            }

            log("Sending hello to server...");
        }

        @Override
        protected String doInBackground ( Void... params ) {
            try {
                if (mChannel == null) {
                    mChannel = OkHttpChannelBuilder.forAddress(mHost, mPort).build();
                }
                GreetingServiceGrpc.GreetingServiceBlockingStub greeterStub = GreetingServiceGrpc.newBlockingStub(
                        mChannel);
                HelloRequest helloRequest = HelloRequest.newBuilder().setName("Android").build();

                HelloResponse helloResponse = greeterStub.greeting(helloRequest);
                return "SERVER: " + helloResponse.getGreeting();
            } catch ( SecurityException | UncheckedExecutionException e ) {
                e.printStackTrace();
                return "ERROR: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute ( String s ) {
            shutdownChannel();
            log(s);
            mSendButton.setEnabled(true);
        }

        @Override
        protected void onCancelled () {
            mSendButton.setEnabled(true);
        }
    }
}
