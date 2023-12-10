package com.vrem.wifianalyzer.wifi.accesspoint

import android.net.DhcpInfo
import android.net.wifi.WifiInfo
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.vrem.wifianalyzer.MainContext
import com.vrem.wifianalyzer.R
import com.vrem.wifianalyzer.databinding.FragmentCollectinfoBinding
import com.vrem.wifianalyzer.http.cookie.https.HttpsUtils
import com.vrem.wifianalyzer.wifi.model.convertIpV4Address
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit


class CollectInfoFragment : Fragment() {

    private val client by lazy {
        OkHttpClient.Builder().apply {
            connectTimeout(30, TimeUnit.SECONDS)
            readTimeout(30, TimeUnit.SECONDS)
            writeTimeout(30, TimeUnit.SECONDS)
            retryOnConnectionFailure(true)
            hostnameVerifier { _, _ -> true }
            val sslParams = HttpsUtils.getSslSocketFactory(null, null, null)
            sslSocketFactory(sslParams.sSLSocketFactory, sslParams.trustManager)
        }.build()
    }

    private var json = JSONObject()

    private val dhcpInfo: DhcpInfo?
        get() = MainContext.INSTANCE.wiFiManagerWrapper.getDhcpInfo()

    private val wifiInfo: WifiInfo?
        get() = MainContext.INSTANCE.wiFiManagerWrapper.wiFiInfo()

    private lateinit var binding: FragmentCollectinfoBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCollectinfoBinding.inflate(inflater, container, false)
        binding.loading.visibility = View.INVISIBLE
        binding.btnUpload.visibility = View.INVISIBLE
        binding.btnUpload.setOnClickListener {
            showInputDescDialog()
        }
        return binding.root
    }

    private fun showInputDescDialog() {
        val layout = MainContext.INSTANCE.layoutInflater.inflate(R.layout.location_input, null)
        val input = layout.findViewById<EditText>(R.id.inputEditText)
        AlertDialog.Builder(requireContext())
            .setTitle("输入位置信息")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                val inputLocation = input.text.toString()
                if (!TextUtils.isEmpty(inputLocation)) {
                    json.put("routerLocationDesc", inputLocation)
                    postInfoToServer()
                } else {
                    Toast.makeText(requireContext(), "输入不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (wifiInfo == null || dhcpInfo == null) {
            Toast.makeText(requireContext(), "没有连接到wifi", Toast.LENGTH_SHORT).show()
        } else {
            json = JSONObject()
            json.put("ssid", wifiInfo!!.ssid)
            json.put("bssid", wifiInfo!!.bssid)
            json.put("lanIp", convertIpV4Address(dhcpInfo!!.ipAddress))
            json.put("gateWayIp", convertIpV4Address(dhcpInfo!!.gateway))
            showLoading()
            displayContent()
            getWanIpInfo()
        }
    }

    private fun displayContent() {
        runOnUiThread {
            val formatJson = JsonParser.parseString(json.toString()).asJsonObject
            binding.btnInfoContent.text =
                GsonBuilder().setPrettyPrinting().create().toJson(formatJson)
        }
    }

    private fun showLoading(show: Boolean = true) {
        binding.loading.visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

    private fun runOnUiThread(callBack: () -> Unit) {
        binding.btnInfo.post(callBack)
    }

    private fun getWanIpInfo() {
        val request = Request.Builder()
            .url("https://api.ip.sb/geoip")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(requireContext(), "获取外网信息失败", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string() ?: ""
                if (!TextUtils.isEmpty(result)) {
                    json.put("wanIpInfo", JSONObject(result))
                    displayContent()
                }
                tryToGetRouterInfo()
            }
        })
    }


    private fun tryToGetRouterInfo() {

        var url = "https://${convertIpV4Address(dhcpInfo!!.gateway)}/"
        getRouterWebInfo(url) {
            if (!it) {
                url = "http://${convertIpV4Address(dhcpInfo!!.gateway)}/"
                getRouterWebInfo(url) { success ->
                    if (success) {
                        showSuccess()
                    }
                }
            } else {
                showSuccess()
            }
        }
    }

    private fun showSuccess() {
        runOnUiThread {
            showLoading(false)
            binding.btnUpload.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "信息收集成功", Toast.LENGTH_SHORT).show()
        }
    }


    /**
     *
     */
    private fun getRouterWebInfo(url: String, isSuccess: (Boolean) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                isSuccess(false)
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(requireContext(), "路由器的信息获取失败：$url", Toast.LENGTH_LONG)
                        .show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string() ?: ""
                val doc = Jsoup.parse(result)
                json.put("routerWebTitle", doc.title())
                json.put("routerWebContent", doc.html())
                displayContent()
                isSuccess(true)
            }
        })
    }

    /**
     *
     */
    private fun postInfoToServer() {
        binding.loading.visibility = View.VISIBLE
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)

        val request: Request = Request.Builder()
            .url("http://192.168.101.9:8080/infoCollect")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    binding.loading.visibility = View.INVISIBLE
                    Toast.makeText(requireContext(), "数据上传失败", Toast.LENGTH_LONG)
                        .show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string() ?: ""
                Log.d("api", result)
                runOnUiThread {
                    binding.loading.visibility = View.INVISIBLE
                    Toast.makeText(requireContext(), "数据上传成功", Toast.LENGTH_LONG)
                        .show()
                }
            }
        })
    }
}