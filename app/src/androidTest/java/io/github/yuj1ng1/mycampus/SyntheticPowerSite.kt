package io.github.yuj1ng1.mycampus

import android.content.Context
import android.graphics.Bitmap
import android.webkit.*
import io.github.yuj1ng1.mycampus.domain.SchoolCalendar
import org.json.JSONArray
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/** Test-only school-shaped page. All requests are intercepted and all data is synthetic. */
class SyntheticPowerSite {
    val room = "合成校区 示例楼 101"
    val requests = CopyOnWriteArrayList<String>()
    val days = (1L..3L).map { LocalDate.now(SchoolCalendar.zone).minusDays(it).toString() }
    fun html(defaultOtherRoom: Boolean = false): String {
        val number = if (defaultOtherRoom) "202" else "101"
        return """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{margin:8px}#tab4{display:none}.table_1,.table_2{display:inline-block;width:45%}input,textarea{display:block}</style>
            <a href="#tab1" onclick="showTab('tab1');return false">水电充值</a>
            <a href="#tab4" onclick="showTab('tab4');return false">房间用量</a>
            <p id="requesting"></p>
            <div id="tab1"><input id="picker-addr" readonly value="合成校区 示例楼 $number">
            <input type="hidden" id="addr" value="demo,building,$number"><input type="hidden" id="addr_copy" value="demo,building,$number">
            <input id="sydl" readonly value="88.88 度"><button onclick="throw Error('Payment must never be called')">确认充值</button></div>
            <div id="tab4"><textarea id="picker-addr-view" readonly>合成校区 示例楼 $number</textarea><input type="hidden" id="addr_view" value="demo,building,$number">
            <ul class="table"><li><div class="table_1">日期</div><div class="table_2">用量</div></li><div class="days">
            <li><div class="table_1">${days[0]}</div><div class="table_2">5.50</div></li>
            <li><div class="table_1">${days[1]}</div><div class="table_2">0</div></li></div></ul></div>
            <script>
            window.yd_roominfo=[{id:'demo',name:'合成校区',subMenus:[{id:'building',name:'示例楼',subMenus:[{id:'101',name:'101'},{id:'202',name:'202'}]}]}];
            function showTab(id){document.getElementById('tab1').style.display=id==='tab1'?'block':'none';document.getElementById('tab4').style.display=id==='tab4'?'block':'none';}
            function query(kind){var address=document.getElementById(kind==='getelec'?'addr':'addr_view').value;
              document.getElementById('requesting').textContent='查询中...';
              fetch('/agent/app/index.php?op='+kind+'&test_room='+encodeURIComponent(address)).then(function(r){return r.json()}).then(function(data){
                if(kind==='getelec')document.getElementById('sydl').value=data.remaining;
                else{var rows='';for(var i=0;i<data.days.length;i++)rows+='<li><div class="table_1">'+data.days[i]+'</div><div class="table_2">'+data.values[i]+'</div></li>';
                  document.querySelector('#tab4 .days').innerHTML=rows;}
                document.getElementById('requesting').textContent='';});
            }
            var balancePicker={params:{onClose:function(){query('getelec')}}};
            var usagePicker={params:{onClose:function(){query('getelecday')}}};
            window.Zepto=function(field){return {data:function(){return field.id==='picker-addr'?balancePicker:usagePicker}}};
            </script>"""
    }

    fun client(delegate: WebViewClient, defaultOtherRoom: Boolean = false) = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, icon: Bitmap?) = delegate.onPageStarted(view, url, icon)
        override fun onPageFinished(view: WebView, url: String?) = delegate.onPageFinished(view, url)
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
            val operation = request.url.getQueryParameter("op")
            if (operation != null) {
                check(operation in setOf("getelec", "getelecday"))
                check(request.url.getQueryParameter("test_room") == "demo,building,101") { "Wrong room was queried" }
                requests += operation
                val response = """{"remaining":"9.75 度","days":${JSONArray(days)},"values":["5.50","0","2.25"]}"""
                return WebResourceResponse("application/json", "UTF-8", response.byteInputStream())
            }
            return WebResourceResponse("text/html", "UTF-8", html(defaultOtherRoom).byteInputStream())
        }
    }

    fun createView(context: Context): WebView = object : WebView(context) {
        override fun setWebViewClient(client: WebViewClient) { super.setWebViewClient(this@SyntheticPowerSite.client(client, true)) }
    }
}
