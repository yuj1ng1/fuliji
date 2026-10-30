package io.github.yuj1ng1.mycampus.domain

import org.junit.Assert.*
import org.junit.Test

class SchoolAccessPolicyTest {
    private val page = "https://hqwx.fjut.edu.cn/agent/app/index.php?i=67&c=entry&do=rechargeelec&m=maihu_yktservice"
    private val signed = page + "&mh_timestamp=20000101000000&mh_sign=0123456789abcdef0123456789abcdef&mh_openid=synthetic%2Bidentity"

    @Test fun credentialsOnlyGoToApprovedVpnOrExactAcademicSso() {
        assertEquals("VPN", SchoolAccessPolicy.credentialOrigin("https://webvpn.fjut.edu.cn/users/sign_in"))
        assertEquals("SSO", SchoolAccessPolicy.credentialOrigin(WebPolicy.SSO_URL))
        listOf(page, "https://jwxt-443.webvpn.fjut.edu.cn/jwglxt/xtgl/login_slogin.html",
            WebPolicy.SSO_URL.replace("jziotlogin", "other"), WebPolicy.SSO_URL + "&service=other",
            "https://webvpn.fjut.edu.cn/users/sign_in/other").forEach { assertNull(SchoolAccessPolicy.credentialOrigin(it)) }
    }

    @Test fun forgedOriginsPortsAndUserInfoAreRejected() {
        listOf("http://webvpn.fjut.edu.cn/users/sign_in", "https://webvpn.fjut.edu.cn.evil.example/users/sign_in",
            "https://webvpn.fjut.edu.cn:444/users/sign_in", "https://person@webvpn.fjut.edu.cn/users/sign_in",
            "javascript:alert(1)").forEach { assertNull(SchoolAccessPolicy.credentialOrigin(it)) }
    }

    @Test fun signedImportRequiresExactReadPageAndUnambiguousQuery() {
        assertTrue(SchoolAccessPolicy.isPowerPage(page))
        assertFalse(SchoolAccessPolicy.isSignedPowerLink(page))
        assertTrue(SchoolAccessPolicy.isSignedPowerLink(signed))
        listOf(signed + "&do=pay", signed.replace("rechargeelec", "other"), signed.replace("mh_sign=012", "mh_sign=xyz"),
            signed.replace("hqwx.fjut.edu.cn", "hqwx.fjut.edu.cn.evil.example"), signed + "&%69=67").forEach {
            assertFalse(SchoolAccessPolicy.isSignedPowerLink(it))
        }
    }

    @Test fun sharedLinkPreservesEncodedParametersAndDropsFragment() {
        assertEquals(signed, SchoolAccessPolicy.extractPowerLink("合成测试链接：" + signed + "#tab4"))
        assertNull(SchoolAccessPolicy.extractPowerLink("普通后勤入口 " + page))
        assertNull(SchoolAccessPolicy.extractPowerLink("https://example.com/?return=" + signed.replace(":", "%3A")))
    }
}
