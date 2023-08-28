function base_url(){
    let url = document.location.origin;
    if(url.includes('localhost')){
        url += '/phpmyadmin/safjweb';
    }
    return url;
}
