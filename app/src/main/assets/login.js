function (config) {
    "use strict";
    function answer(state) { return JSON.stringify({state: state}); }
    var vpn = location.protocol === "https:" && location.hostname === "webvpn.fjut.edu.cn" &&
        /^(?:\/|\/login\/?|\/users\/sign_in)?$/.test(location.pathname);
    var sso = location.protocol === "https:" && location.hostname === "nids-443.webvpn.fjut.edu.cn" &&
        location.pathname === "/authserver/login" && new URL(location.href).searchParams.get("service") ===
        "https://jwxt-443.webvpn.fjut.edu.cn/sso/jziotlogin";
    if (!((config.kind === "VPN" && vpn) || (config.kind === "SSO" && sso))) return answer("blocked");
    function visible(node) {
        if (!node || node.type === "hidden") return false;
        for (var current = node; current && current.nodeType === 1; current = current.parentElement) {
            var style = getComputedStyle(current);
            if (style.display === "none" || style.visibility === "hidden") return false;
        }
        return true;
    }
    var user = document.querySelector(vpn ? "#user_login" : "#username, input[name=username]");
    var pass = document.querySelector(vpn ? "#user_password" : "#password, input[name=password]");
    if (!visible(user) || !visible(pass)) return answer("wait");
    var inputs = document.querySelectorAll("input");
    for (var i = 0; i < inputs.length; i++) {
        if (visible(inputs[i]) && /captcha|rucaptcha|verifycode|yzm|otp|dynamiccode/i.test(inputs[i].id + " " + inputs[i].name)) {
            return answer("manual");
        }
    }
    var errors = document.querySelectorAll(".auth_error, .alert-danger, #formErrorTip, #showErrorTip, #errorMsg");
    for (var j = 0; j < errors.length; j++) {
        if (visible(errors[j]) && /错误|失败|锁定|不正确/.test(errors[j].textContent)) return answer("rejected");
    }
    var button = document.querySelector(vpn ? "input[name=commit][type=submit], button[type=submit]" : "#login_submit");
    if (!visible(button)) return answer("wait");
    if (sso) {
        var remember = document.querySelector("#rememberMe");
        if (remember && !remember.dataset.rongjiConfigured) {
            remember.checked = !!config.remember;
            remember.dataset.rongjiConfigured = "yes";
            remember.dispatchEvent(new Event("change", {bubbles: true}));
        }
    }
    if (config.inspectOnly) return answer("form");
    var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set;
    setter.call(user, config.username);
    setter.call(pass, config.password);
    user.dispatchEvent(new Event("input", {bubbles: true}));
    user.dispatchEvent(new Event("change", {bubbles: true}));
    pass.dispatchEvent(new Event("input", {bubbles: true}));
    pass.dispatchEvent(new Event("change", {bubbles: true}));
    button.click();
    return answer("submitted");
}
