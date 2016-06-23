package edu.hrbeu.ice.rabbitmqclient;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class MainActivity extends AppCompatActivity {

    ConnectionFactory factory = new ConnectionFactory();
    Thread subscribeThread;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //连接设置
        setupConnectionFactory();

        //用于从线程中获取数据，更新ui
        final Handler incomingMessageHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                String message = msg.getData().getString("msg");
                TextView tv = (TextView) findViewById(R.id.textView);
                Date now = new Date();
                SimpleDateFormat ft = new SimpleDateFormat("hh:mm:ss");
                tv.append(ft.format(now) + ' ' + message + '\n');
                Log.i("test", "msg:" + message);
            }
        };
        //开启消费者线程
        subscribe(incomingMessageHandler);
    }

    /**
     * 连接设置
     */
    private void setupConnectionFactory() {
        factory.setHost("server_url");
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");
    }

    /**
     * 消费者线程
     */
    void subscribe(final Handler handler) {
        subscribeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        //使用之前的设置，建立连接
                        Connection connection = factory.newConnection();
                        //创建一个通道
                        Channel channel = connection.createChannel();
                        //一次只发送一个，处理完成一个再获取下一个
                        channel.basicQos(1);

                        AMQP.Queue.DeclareOk q = channel.queueDeclare();
                        //将队列绑定到消息交换机exchange上
                        //                  queue         exchange              routingKey路由关键字，exchange根据这个关键字进行消息投递。
                        channel.queueBind(q.getQueue(), "CAR_MONITOR_EXCHANGE", "CSHARP.*");

                        //创建消费者
                        QueueingConsumer consumer = new QueueingConsumer(channel);
                        channel.basicConsume(q.getQueue(), true, consumer);

                        while (true) {
                            //wait for the next message delivery and return it.
                            QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                            String message = new String(delivery.getBody());

                            Log.d("", "[r] " + message);

                            //从message池中获取msg对象更高效
                            Message msg = handler.obtainMessage();
                            Bundle bundle = new Bundle();
                            bundle.putString("msg", message);
                            msg.setData(bundle);
                            handler.sendMessage(msg);
                        }
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e1) {
                        Log.d("", "Connection broken: " + e1.getClass().getName());
                        try {
                            Thread.sleep(5000); //sleep and then try again
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            }
        });
        subscribeThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subscribeThread.interrupt();
    }

}
