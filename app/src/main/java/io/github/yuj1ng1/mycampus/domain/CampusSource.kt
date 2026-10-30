package io.github.yuj1ng1.mycampus.domain

import java.net.URI

enum class CampusSource(val label: String, val entryUrl: String, val expectedPath: String) {
    COURSES("课表", "https://jwxt-443.webvpn.fjut.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default", "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html"),
    EXAMS("考试安排", "https://jwxt-443.webvpn.fjut.edu.cn/jwglxt/kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N358105&layout=default", "/jwglxt/kwgl/kscx_cxXsksxxIndex.html"),
    GRADES("成绩", "https://jwxt-443.webvpn.fjut.edu.cn/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default", "/jwglxt/cjcx/cjcx_cxDgXscj.html"),
    POWER("宿舍电量", "https://hqwx.fjut.edu.cn/agent/app/index.php?i=67&c=entry&do=rechargeelec&m=maihu_yktservice&mh_skipLoading=1", "/agent/app/index.php"),
    POWER_USAGE("房间用量", "https://hqwx.fjut.edu.cn/agent/app/index.php?i=67&c=entry&do=rechargeelec&m=maihu_yktservice&mh_skipLoading=1#tab4", "/agent/app/index.php");

    fun matchesResultUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        val expectedHost = URI(entryUrl).host
        WebPolicy.isSchoolUrl(url) && uri.host.equals(expectedHost, true) && uri.path == expectedPath &&
            (isAcademic || SchoolAccessPolicy.isPowerPage(url))
    }.getOrDefault(false)

    val isAcademic: Boolean get() = this !in setOf(POWER, POWER_USAGE)
}

object WebPolicy {
    const val VPN_URL = "https://webvpn.fjut.edu.cn/"
    const val SSO_URL = "https://nids-443.webvpn.fjut.edu.cn/authserver/login?service=https%3A%2F%2Fjwxt-443.webvpn.fjut.edu.cn%2Fsso%2Fjziotlogin"
    const val CALENDAR_URL = "https://jwc.fjut.edu.cn/2026/0701/c12011a269787/page.htm"
    private val allowedHosts = setOf(
        "vpnsite.fjut.edu.cn", "webvpn.fjut.edu.cn", "nids-443.webvpn.fjut.edu.cn",
        "jwxt-443.webvpn.fjut.edu.cn", "hqwx.fjut.edu.cn",
    )

    fun isSchoolUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", true) && uri.userInfo == null &&
            uri.port in setOf(-1, 443) && uri.host?.lowercase() in allowedHosts
    }.getOrDefault(false)
}
