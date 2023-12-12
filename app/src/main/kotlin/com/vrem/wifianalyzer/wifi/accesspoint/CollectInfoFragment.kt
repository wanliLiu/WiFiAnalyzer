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
            val ipAddress = convertIpV4Address(dhcpInfo!!.ipAddress)
            val gateWayAddress = convertIpV4Address(dhcpInfo!!.gateway)
            json.put("ssid", wifiInfo!!.ssid)
            json.put("bssid", wifiInfo!!.bssid)
            json.put("lanIp", ipAddress)
            json.put("gateWayIp", gateWayAddress)
            displayContent()
            if (!TextUtils.isEmpty(ipAddress) && !TextUtils.isEmpty(gateWayAddress)) {
                val vendorName = MainContext.INSTANCE.vendorService.findVendorName(wifiInfo!!.bssid)
                json.put("routerVendor", vendorName)
                showLoading()
                getWanIpInfo()
            } else {
                Toast.makeText(requireContext(), "ip地址或者网关地址为空", Toast.LENGTH_SHORT)
                    .show()
            }
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
                    binding.btnUpload.visibility = View.VISIBLE
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
            .url("http://8.213.130.38:28311/report/post_report")
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
                val code = JSONObject(result).optInt("code", 0)
                Log.d("api", result)
                runOnUiThread {
                    binding.loading.visibility = View.INVISIBLE
                    Toast.makeText(requireContext(), if (code == 200)"数据上传成功" else "数据上传失败！！！！", Toast.LENGTH_LONG)
                        .show()
                }
            }
        })
    }
}