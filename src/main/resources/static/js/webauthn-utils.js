function base64urlEncode(buffer) {
    var base64 = btoa(String.fromCharCode.apply(null, new Uint8Array(buffer)));
    return base64.replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}
function base64urlDecode(base64url) {
    var base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
    var binary = atob(base64);
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.buffer;
}
function getCsrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]').content;
    var header = document.querySelector('meta[name="_csrf_header"]').content;
    var headers = {};
    headers[header] = token;
    return headers;
}
function postJson(url, headers, body) {
    var options = {
        method: 'POST',
        headers: Object.assign({ 'Content-Type': 'application/json' }, headers)
    };
    if (body) {
        options.body = JSON.stringify(body);
    }
    return fetch(url, options);
}
