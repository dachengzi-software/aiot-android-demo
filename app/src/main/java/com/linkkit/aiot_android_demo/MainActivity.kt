package com.linkkit.aiot_android_demo

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AiotMqtt"

        /* 设备三元组信息 */
        private const val PRODUCT_KEY = "a11xsrWmW14"
        private const val DEVICE_NAME = "paho_android"
        private const val DEVICE_SECRET = "tLMT9QWD36U2SArglGqcHCDK9rK9nOrA"

        /* 自动Topic, 用于上报消息 */
        private const val PUB_TOPIC = "/$PRODUCT_KEY/$DEVICE_NAME/user/update"

        /* 自动Topic, 用于接受消息 */
        private const val SUB_TOPIC = "/$PRODUCT_KEY/$DEVICE_NAME/user/get"

        /* 阿里云Mqtt服务器域名 */
        private const val HOST = "tcp://$PRODUCT_KEY.iot-as-mqtt.cn-shanghai.aliyuncs.com:443"
    }

    private val mTvContent by lazy { findViewById<TextView>(R.id.tv_content) }

    private val mBtnPublish by lazy { findViewById<Button>(R.id.btn_publish) }

    private val triple: Triple<String?, String?, String?> by lazy {
        var clientId: String? = null
        var userName: String? = null
        var passWord: String? = null

        /* 获取Mqtt建连信息clientId, username, password */
        val aiotMqttOption = AiotMqttOption().getMqttOption(PRODUCT_KEY, DEVICE_NAME, DEVICE_SECRET)
        if (aiotMqttOption == null) {
            Log.e(TAG, "device info error  ${Thread.currentThread().name}")
        } else {
            clientId = aiotMqttOption.clientId
            userName = aiotMqttOption.username
            passWord = aiotMqttOption.password
        }
        Triple(clientId, userName, passWord)
    }

    /* 创建MqttAndroidClient对象, 并设置回调接口 */
    private val mqttAndroidClient: MqttAndroidClient by lazy {
        MqttAndroidClient(applicationContext, HOST, triple.first).apply {
            setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.i(TAG, "connection lost  ${Thread.currentThread().name}")
                }

                @Throws(Exception::class)
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val msg = message?.payload?.toString(Charsets.UTF_8)
                    Log.i(TAG, "topic: $topic, msg: $msg  ${Thread.currentThread().name}")
                    mTvContent.text = msg
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.i(TAG, "msg delivered  ${Thread.currentThread().name}")
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectMqtt()

        /* 通过按键发布消息 */
        mBtnPublish.setOnClickListener {
            subscribeTopic(SUB_TOPIC, "hello IoT  ${Thread.currentThread().name} " + TimeUtils.getNowString(TimeUtils.getSafeDateFormat("yyyy-MM-dd HH:mm:ss.SSS")))
        }

    }

    private fun connectMqtt() {
        /* Mqtt建连 */
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                /* 创建MqttConnectOptions对象并配置username和password */
                val mqttConnectOptions = MqttConnectOptions()
                mqttConnectOptions.userName = triple.second
                mqttConnectOptions.password = triple.third?.toCharArray()

                mqttAndroidClient.connect(mqttConnectOptions, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "connect succeed")
                        subscribeTopic(SUB_TOPIC, "hello IoT 初始化，第一次发送消息  ${Thread.currentThread().name} " + TimeUtils.getNowString(TimeUtils.getSafeDateFormat("yyyy-MM-dd HH:mm:ss.SSS")))
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.i(TAG, "connect failed  ${Thread.currentThread().name}")
                    }
                })
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 订阅特定的主题
     *
     * @param topic mqtt主题
     */
    fun subscribeTopic(topic: String?, payload: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                mqttAndroidClient.subscribe(topic, 0, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "subscribed succeed ${Thread.currentThread().name}")
                        publishMessage(payload)
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.i(TAG, "subscribed failed ${Thread.currentThread().name}")
                    }
                })
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 向默认的主题/user/update发布消息
     *
     * @param payload 消息载荷
     */
    fun publishMessage(payload: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!mqttAndroidClient.isConnected) {
                    mqttAndroidClient.connect()
                }
                val message = MqttMessage()
                message.payload = payload.toByteArray()
                message.qos = 0
                mqttAndroidClient.publish(PUB_TOPIC, message, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken) {
                        Log.i(TAG, "publish succeed!")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                        Log.i(TAG, "publish failed!")
                    }
                })
            } catch (e: MqttException) {
                Log.e(TAG, e.toString())
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disConnect()
    }

    /**
     * 取消连接
     *
     * @throws Exception
     */
    private fun disConnect() {

        LogUtils.dTag(TAG, "DisConnecting")

        if (!mqttAndroidClient.isConnected) {
            LogUtils.dTag(TAG, "disConnect failure", "mqttClient is not connected")
            return
        }

        try {
            mqttAndroidClient.disconnect(null, object : IMqttActionListener {

                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    LogUtils.dTag(TAG, "disConnect success", asyncActionToken)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    LogUtils.eTag(TAG, "disConnect failure", asyncActionToken, exception)
                }

            })
        } catch (e: MqttException) {
            LogUtils.eTag(TAG, e.message)
        }

    }


    /**
     * MQTT建连选项类，输入设备三元组productKey, deviceName和deviceSecret, 生成Mqtt建连参数clientId，username和password.
     */
    internal inner class AiotMqttOption {
        var username = ""
            private set
        var password = ""
            private set
        var clientId = ""
            private set

        /**
         * 获取Mqtt建连选项对象
         *
         * @param productKey   产品秘钥
         * @param deviceName   设备名称
         * @param deviceSecret 设备机密
         * @return AiotMqttOption对象或者NULL
         */
        fun getMqttOption(productKey: String?, deviceName: String?, deviceSecret: String?): AiotMqttOption? {
            if (productKey == null || deviceName == null || deviceSecret == null) {
                return null
            }
            try {
                val timestamp = java.lang.Long.toString(System.currentTimeMillis())

                // clientId
                this.clientId = productKey + "." + deviceName + "|timestamp=" + timestamp +
                        ",_v=paho-android-1.0.0,securemode=2,signmethod=hmacsha256|"

                // userName
                username = "$deviceName&$productKey"

                // password
                val macSrc = "clientId" + productKey + "." + deviceName + "deviceName" +
                        deviceName + "productKey" + productKey + "timestamp" + timestamp
                val algorithm = "HmacSHA256"
                val mac = Mac.getInstance(algorithm)
                val secretKeySpec = SecretKeySpec(deviceSecret.toByteArray(), algorithm)
                mac.init(secretKeySpec)
                val macRes = mac.doFinal(macSrc.toByteArray())
                password = String.format("%064x", BigInteger(1, macRes))
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
            return this
        }
    }
}