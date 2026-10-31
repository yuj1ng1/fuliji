package io.github.yuj1ng1.mycampus.domain

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object SchoolAccessPolicy {
    private fun parameters(url: String): Map<String, String> {
        val pairs = URI(url).rawQuery.orEmpty().split('&').filter(String::isNotBlank).map { part ->
            val split = part.split('=', limit = 2)
            URLDecoder.decode(split[0], StandardCharsets.UTF_8.name()) to
                URLDecoder.decode(split.getOrElse(1) { "" }, StandardCharsets.UTF_8.name())
        }
        require(pairs.map { it.first }.distinct().size == pairs.size)
        return pairs.toMap()
    }

    fun isPowerPage(url: String): Boolean = runCatching {
        val uri = URI(url)
        val query = parameters(url)
        WebPolicy.isSchoolUrl(url) && uri.host.equals("hqwx.fjut.edu.cn", true) &&
            uri.path == "/agent/app/index.php" && query["i"] == "67" && query["c"] == "entry" &&
            query["do"] == "rechargeelec" && query["m"] == "maihu_yktservice"
    }.getOrDefault(false)

    /** Only a user-supplied, already-authorized school link; this cannot manufacture authorization. */
    fun isSignedPowerLink(url: String): Boolean = runCatching {
        val query = parameters(url)
        url.length <= 8192 && isPowerPage(url) &&
            query["mh_timestamp"]?.matches(Regex("\\d{14}")) == true &&
            query["mh_sign"]?.matches(Regex("[A-Fa-f0-9]{16,128}")) == true &&
            query["mh_openid"]?.let { it.isNotBlank() && it.length <= 512 } == true
    }.getOrDefault(false)

    fun extractPowerLink(text: String): String? {
        val candidates = Regex("https://[^\\s<>\"']+", RegexOption.IGNORE_CASE).findAll(text.take(16_384))
        return candidates.map { it.value.trimEnd(')', '）', '，', '。') }
            .firstOrNull(::isSignedPowerLink)?.substringBefore('#')
    }

    fun extractPowerEntry(text: String): String? {
        val candidates = Regex("https://[^\\s<>\"']+", RegexOption.IGNORE_CASE).findAll(text.take(16_384))
        return candidates.map { it.value.replace("&amp;", "&").trimEnd(')', '）', '，', '。') }
            .firstOrNull { it.length <= 8192 && isPowerPage(it) }?.substringBefore('#')
    }

    fun credentialOrigin(url: String): String? = runCatching {
        if (!WebPolicy.isSchoolUrl(url)) return null
        val uri = URI(url)
        when {
            uri.host.equals("webvpn.fjut.edu.cn", true) && uri.path in setOf("", "/", "/login", "/login/", "/users/sign_in") -> "VPN"
            uri.host.equals("nids-443.webvpn.fjut.edu.cn", true) && uri.path == "/authserver/login" &&
                parameters(url)["service"] == "https://jwxt-443.webvpn.fjut.edu.cn/sso/jziotlogin" -> "SSO"
            else -> null
        }
    }.getOrNull()

    fun allowsPowerIdentityFields(url: String): Boolean = runCatching {
        val uri = URI(url)
        val query = parameters(url)
        WebPolicy.isSchoolUrl(url) && uri.host.equals("hqwx.fjut.edu.cn", true) && uri.path == "/agent/app/index.php" &&
            query["i"] == "67" && query["c"] == "entry" && query["do"] == "register" && query["m"] == "maihu_portal"
    }.getOrDefault(false)

    fun isAcademicLocalLogin(url: String): Boolean = runCatching {
        val uri = URI(url)
        WebPolicy.isSchoolUrl(url) && uri.host.equals("jwxt-443.webvpn.fjut.edu.cn", true) &&
            uri.path == "/jwglxt/xtgl/login_slogin.html"
    }.getOrDefault(false)
}
