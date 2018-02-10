package com.troshchiy.n2ochat.ui

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.softwarejoint.bert.BertAtom
import com.softwarejoint.bert.BertDecoder
import com.softwarejoint.bert.BertEncoder
import com.softwarejoint.bert.BertTuple
import com.troshchiy.n2ochat.R
import com.troshchiy.n2ochat.string
import kotlinx.android.synthetic.main.activity_main.*
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.jetbrains.anko.*
import java.io.InvalidObjectException

class MainActivity : AppCompatActivity(), AnkoLogger {

    private lateinit var mqttAndroidClient: MqttAndroidClient
    private var startTime = 0L

    private var token: ByteArray? = null
    private val bufferSize = 1024 * 8 //8kb default size
    private val clientId = "emqttd_30gph465el7ja8oawvi"
    private val topic = "room/global"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        info { "onCreate" }

        mqttAndroidClient = getMqttAndroidClient(topic)
        connectToMqttAndroidClient(topic)

        btn_send.setOnClickListener { publishMessage() }
    }

    private fun getMqttAndroidClient(topic: String) = MqttAndroidClient(applicationContext, "tcp://ns.synrc.com:1883", clientId).apply {
        setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                info { "connectComplete" }
                subscribeToTopic(topic)
            }

            override fun connectionLost(cause: Throwable) {
                info { "connectionLost" }
            }

            @Throws(Exception::class)
            override fun messageArrived(topic: String, message: MqttMessage) {
                info { "messageArrived. topic: $topic, message: $message" }
                messageArrived(message)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken) {
                info { "deliveryComplete. token: ${token.message.payload}" }
                this@MainActivity.token = token.message.payload
                tv_latency.text = this@MainActivity.string(R.string.latency, (System.currentTimeMillis() - startTime).toString())
            }
        })
    }

    private fun subscribeToTopic(topic: String) {
        try {
            mqttAndroidClient.subscribe(arrayOf(topic, "actions/1/index/$clientId/"), intArrayOf(1, 1), null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    info { "subscribeToTopic. onSuccess" }
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    error("subscribeToTopic. onFailure", exception)
                    toast(exception.message.toString())
                }
            })
        } catch (ex: MqttException) {
            error("subscribeToTopic", ex)
        }
    }

    private fun messageArrived(mqttMessage: MqttMessage) {
        info { "messageArrived. payload: ${mqttMessage.payload}" }

        var decodedPayload: Any? = null
        try {
            decodedPayload = BertDecoder.setupDecoder().decodeAny(mqttMessage.payload)
            debug { "messageArrived\n\ndecodedPayload: $decodedPayload" }
        } catch (e: InvalidObjectException) {
            error("decoded message payload", e)
        }

        if (decodedPayload is BertTuple) {
            val message = decodedPayload[1]
            if (message is ByteArray) tv_message.text = String(message)
        }
    }

    private fun connectToMqttAndroidClient(topic: String) {
        try {
            mqttAndroidClient.connect(getMqttConnectOptions(), null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    info { "connectToMqttAndroidClient. onSuccess" }

                    mqttAndroidClient.setBufferOpts(getDisconnectedBufferOptions())

                    subscribeToTopic(topic)

                    publishMessage()
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    error("onFailure", exception)
                    toast(exception.message.toString())
                }
            })
        } catch (ex: MqttException) {
            error("connectToMqttAndroidClient. onFailure", ex)
        }
    }

    private fun getMqttConnectOptions() = MqttConnectOptions().apply {
        userName = "api"
        isAutomaticReconnect = true
        isCleanSession = true
    }

    private fun getDisconnectedBufferOptions() = DisconnectedBufferOptions().apply {
        isBufferEnabled = true
        bufferSize = 100
        isPersistBuffer = false
        isDeleteOldestMessages = false
    }

    private fun publishMessage() {
        //TODO: Check is connected
        info { "publishMessage" }

        startTime = System.currentTimeMillis()

        val bertTuple = BertTuple().apply {
            add(BertAtom("message"))
            add(edt_message.text.toString())
        }

        val message = MqttMessage().apply {
            payload = BertEncoder.setupEncoder()
                    .withBufferSize(bufferSize)
                    .withEncodeStringAsBinary(true)
                    ?.encodeAny(bertTuple)

            qos = 1
        }

        try {
            //TODO: Show progress
            mqttAndroidClient.publish("events/1/3/index/anon/$clientId/", message)
            edt_message.setText("")
        } catch (e: Exception) {
            error("publishMessage", e)
        }
    }
}