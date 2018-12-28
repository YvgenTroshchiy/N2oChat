package com.troshchiy.n2ochat.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
import java.util.*


//TODO: Split this class to avoid god object. Extract to the manager/domain
class MainActivity : AppCompatActivity(), AnkoLogger {

    private lateinit var mqttAndroidClient: MqttAndroidClient
    private var startTime = 0L

    private var token: ByteArray? = null
    private val bufferSize = 1024 * 8 //8kb default size

    // TODO: Replace with the [private val clientId = UUID.randomUUID().toString()]
    private val clientId = "emqttd_30gph465el7ja8oawvi"
    private val topic = "room/global"

    private val history = "History"
    private val message = "Message"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        info { "onCreate" }

        mqttAndroidClient = getMqttAndroidClient(topic)
        connectToMqttAndroidClient(topic)

        btnSend.setOnClickListener { publishMessage() }
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
                tvLatency.text = this@MainActivity.string(R.string.latency, (System.currentTimeMillis() - startTime).toString())
            }
        })
    }

    private fun subscribeToTopic(topic: String) {
        try {
            mqttAndroidClient.subscribe(arrayOf(topic, "actions/1/index/$clientId/"), intArrayOf(1, 1), null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    info { "subscribeToTopic. onSuccess" }
                    getHistory()
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

        val decodedPayload: Any? = try {
            BertDecoder.setupDecoder().decodeAny(mqttMessage.payload)
        } catch (e: InvalidObjectException) {
            error("decoded message payload", e)
            null
        }

        debug { "messageArrived. decodedPayload: $decodedPayload" }

        if (decodedPayload is BertTuple) {
            val firstElement = decodedPayload[0]
            if (firstElement is BertAtom) {
                when (firstElement.get()) {
                    history -> {
                        val historyData: ArrayList<BertTuple> = decodedPayload[4] as ArrayList<BertTuple>

                        for (message in historyData) {
                            val message = String(message[14] as ByteArray)
                            warn { message }
                        }

                    }
                    message -> {
                        val message = decodedPayload[14]
                        if (message is ByteArray) tvMessage.text = String(message)
                    }
                }
            }
        }
    }

    private fun connectToMqttAndroidClient(topic: String) {
        try {
            mqttAndroidClient.connect(getMqttConnectOptions(), null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    info { "connectToMqttAndroidClient. onSuccess" }

                    mqttAndroidClient.setBufferOpts(getDisconnectedBufferOptions())

                    subscribeToTopic(topic)
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

    private fun getHistory() {
        //TODO: Check is connected
        info { "getHistory" }

        val bertTupleLoad = BertTuple().apply {
            add(BertAtom("load"))
            add("")
        }

        val load = MqttMessage().apply {
            payload = BertEncoder.setupEncoder()
                .withBufferSize(bufferSize)
                .withEncodeStringAsBinary(true)
                ?.encodeAny(bertTupleLoad)

            qos = 1
        }

        try {
            //TODO: Show progress
            //TODO: Create endpoint constant
            mqttAndroidClient.publish("events/1/3/index/anon/$clientId/", load)
        } catch (e: Exception) {
            error("getHistory", e)
        }
    }

    private fun publishMessage() {
        //TODO: Check is connected
        info { "publishMessage" }

        startTime = System.currentTimeMillis()

        val message = MqttMessage().apply {
            val bertTuple = BertTuple().apply {
                add(BertAtom("message"))
                add(edtMessage.text.toString())
            }

            payload = BertEncoder.setupEncoder()
                .withBufferSize(bufferSize)
                .withEncodeStringAsBinary(true)
                ?.encodeAny(bertTuple)

            // Quality of Service 1 - indicates that a message should be delivered at least once (one or more times).
            qos = 1
        }

        try {
            //TODO: Show progress
            mqttAndroidClient.publish("events/1/3/index/anon/$clientId/", message)
            edtMessage.setText("")
        } catch (e: Exception) {
            error("publishMessage", e)
        }
    }
}