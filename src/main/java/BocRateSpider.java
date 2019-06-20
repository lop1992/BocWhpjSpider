import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @Author: lop1992
 * @Date: 2019/6/19 16:18
 * 毕竟写代码才不花钱啊
 * 中行外汇牌价爬虫
 **/
public class BocRateSpider {
    private static final String BOCSEARCHURL = "http://srh.bankofchina.com/search/whpj/search.jsp";

    public static void main(String[] args) {
        String currency ="USD";
        int index = 1;
        String start =null;
        String end =null;
        if(args.length>=1 ){
             currency = args[0];
        }
        if(args.length>=2 ){
             try {
                 index = Integer.parseInt(args[1]);
             }catch (Exception e){
                index = 1;
             }
        }
        if(args.length>=3 ){
             start = args[2];
        }
        if(args.length>=4 ){
             end = args[3];
        }

        List<BocRate> r=getBankExchangeRate(currency,start,end,index);
        int i = 0;
        System.out.println("货币名称\t现汇买入价\t现钞买入价\t现汇卖出价\t现钞卖出价\t中行折算价\t发布时间\t币种");
        for (BocRate v: r) {
            System.out.println(++i +","+ v.bocFormatString());
        }
    }

    /**
     *
     * @param currency 币种
     * @param start 开始时间
     * @param end 结束时间
     * @param index 条数   (当 index > 0 时 取最新的前几条,  当index< 0 时,从末尾开始取,当 Index = 0,取全部)
     * @return
     */
    public static List<BocRate> getBankExchangeRate(String currency, String start,String end,int index){
        List<BocRate> res =new ArrayList<>();
        if(currency ==null || !CODECURRENCY.containsKey(currency.trim().toUpperCase())){return  null;}
        String fx = CODECURRENCY.get(currency.trim().toUpperCase());
        String d1 = start==null?LocalDate.now().toString():start;
        String d2 = end==null?LocalDate.now().toString():end;

        List<NameValuePair> formparams = new ArrayList<NameValuePair>();

        //第一次查询
        String page1Content = sendHttp(fx,d1,d2,1);
        int allPage = getAllPage(page1Content);


        if(allPage >0){
            if (index == 0) {
                res.addAll(parseContent(page1Content));
                for (int i = 2; i <= allPage; i++) {
                    int newPageall = getAllPage(page1Content);
                    if (allPage != newPageall) {
                        allPage = newPageall;
                    }
                    res.addAll(parseContent(sendHttp(fx, d1, d2, i)));
                }
            } else if (index > 0) {
                res.addAll(parseContent(page1Content));
                int wantPage = (index + 20 - 1) / 20;
                if (wantPage < allPage) {
                    allPage = wantPage;
                }
                for (int i = 2; i <= allPage; i++) {
                    res.addAll(parseContent(sendHttp(fx, d1, d2, i)));
                }
                res = res.subList(0, index > res.size() ? res.size() : index);
            } else {
                int absIndex  = Math.abs(index);
                if(allPage == 1){
                    List<BocRate> s = parseContent(page1Content);
                    Collections.reverse(s);
                    res.addAll(s.subList(0, absIndex > s.size() ? s.size() : absIndex ));
                }else{
                    for (int i = allPage; i >= 0 && res.size()< absIndex; i--) {
                        List<BocRate> s = parseContent(sendHttp(fx, d1, d2, i));
                        Collections.reverse(s);
                        res.addAll(s);
                    }
                    res = res.subList(0,absIndex > res.size() ? res.size() : absIndex);
                }
            }

        }
        res.parallelStream().forEach(s -> s.setCurrency(currency));
//        return res.stream().distinct().collect(Collectors.toList());
        return res;
    }

    private static int getAllPage(String page1Content) {
        //总页数
        String totalRecord = getMatcher("var m_nRecordCount = (.*);",page1Content);
        int allPage = 0;
        try {
            if (totalRecord ==null){
                allPage = 0;
            }
            allPage = (Integer.parseInt(totalRecord) + 20 - 1)/20 ;
            /*
            计算总页数     
            起始页   等于   总记录数 + 页显示最大记录数的结果  除以  页显示最大记录数的结果
            int totalPageNum = (totalRecord + pageSize - 1) / pageSize; 
             */
        }catch (Exception e){
            e.printStackTrace();
        }
        return allPage;
    }


    private static List<BocRate> parseContent(String page1Content) {
        List<BocRate> res = new ArrayList<>();
        Document doc = Jsoup.parse(page1Content);
        Elements div = doc.getElementsByClass("BOC_main");
        if(div != null && !div.isEmpty()){
            Elements tables = doc.getElementsByTag("table");
            if(tables!=null && !tables.isEmpty()){
                Elements trs = doc.getElementsByTag("tr");
                    if(trs!=null && !trs.isEmpty()){
                        for (Element e:
                             trs) {
                            Elements tds = e.getElementsByTag("td");
                            if(tds !=null && !tds.isEmpty() &&tds.size()==7){
                                    String d = tds.get(6).text();
                                    String date="";
                                    String time="";
                                    if(d != null){
                                        String[] split = d.trim().replaceAll("[\\.]","-").split(" ");
                                        if (split.length == 2){
                                            date = split[0];
                                            time = split[1];
                                        }
                                    }
                                    res.add(new BocRate()
                                        .setCurrencyName(tds.get(0).text())
                                        .setExchangeBuy(readTextToNumber(tds.get(1).text()))
                                        .setCashBuy(readTextToNumber(tds.get(2).text()))
                                        .setExchangeSale(readTextToNumber(tds.get(3).text()))
                                        .setCashSale(readTextToNumber(tds.get(4).text()))
                                        .setBocDisPrice(readTextToNumber(tds.get(5).text()))
                                        .setRelesDate(date)
                                        .setRelesTime(time)
                                    );
                            }
                        }
                    }
            }
        }
        return res;
    }

    private static String sendHttp(String currency, String start,String end,int page){
        HttpPost httpPost = getHttpPost();
        List<NameValuePair> formparams = new ArrayList<NameValuePair>(4);
        formparams.add(new BasicNameValuePair("erectDate", start.toString()));
        formparams.add(new BasicNameValuePair("nothing", end.toString()));
        formparams.add(new BasicNameValuePair("pjname", currency));
        formparams.add(new BasicNameValuePair("page", String.valueOf(page)));


        UrlEncodedFormEntity uefEntity = null;
        try {
            uefEntity = new UrlEncodedFormEntity(formparams, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        httpPost.setEntity(uefEntity);
        //设置进去
        CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(2000).setConnectTimeout(2000).setSocketTimeout(2000).build()).build();

        CloseableHttpResponse response = null;
        StringBuffer out;
        try {
            response = httpClient.execute(httpPost);

            HttpEntity entity = response.getEntity();

            InputStream in = entity.getContent();

            out = new StringBuffer();
            byte[] b = new byte[4096];
            for (int n; (n = in.read(b)) != -1;) {
                out.append(new String(b, 0, n));
            }
            in = null;
            return out.toString();
        } catch (ClientProtocolException e) {
            System.err.println(e.getMessage());
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } finally{
            try {
                if (httpClient!=null) {
                    httpClient.close();
                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
        return null;
    }

    private static BigDecimal readTextToNumber(String text){
        if(text !=null && NumberUtils.isNumber(text)){
            return new BigDecimal(text);
        }
            return new BigDecimal(0);
    }

    private static  HttpPost getHttpPost() {

        RequestConfig config = RequestConfig.custom()
                         .setConnectionRequestTimeout(10000).setConnectTimeout(10000)
                         .setSocketTimeout(10000).build();
        HttpPost httpPost = new HttpPost(BOCSEARCHURL);
        httpPost.setConfig(config);
        httpPost.setHeader("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3");
        httpPost.setHeader("Accept-Encoding","gzip, deflate");
        httpPost.setHeader("Accept-Language","en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7");
        httpPost.setHeader("Cache-Control","max-age=0");
        httpPost.setHeader("Connection","keep-alive");
        httpPost.setHeader("Content-Type","application/x-www-form-urlencoded");
        httpPost.setHeader("Cookie","JSESSIONID=0000wFq2w_ae0XODom72SBku0H9:-1");
        httpPost.setHeader("Host","srh.bankofchina.com");
        httpPost.setHeader("Origin","http://srh.bankofchina.com");
        httpPost.setHeader("Referer","http://srh.bankofchina.com/search/whpj/search.jsp");
        httpPost.setHeader("Upgrade-Insecure-Requests","1");
        httpPost.setHeader("User-Agent","Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36");
        return httpPost;
    }
    static class BocRate {
        private String currencyName;// 货币名称
        private String currency;// 货币简称：如USD
        private BigDecimal exchangeBuy;// 现汇买入价
        private BigDecimal cashBuy;// 现钞买入价
        private BigDecimal exchangeSale;// 现汇卖出价
        private BigDecimal cashSale;// 现钞卖出价
        private BigDecimal bocDisPrice;// 中行折算价
        private String relesDate;// 发布日期
        private String relesTime;// 发布时间

        public String getCurrencyName() {
            return currencyName;
        }

        public BocRate setCurrencyName(String currencyName) {
            this.currencyName = currencyName;
            return this;
        }

        public String getCurrency() {
            return currency;
        }

        public BocRate setCurrency(String currency) {
            this.currency = currency;
            return this;
        }

        public BigDecimal getExchangeBuy() {
            return exchangeBuy;
        }

        public BocRate setExchangeBuy(BigDecimal exchangeBuy) {
            this.exchangeBuy = exchangeBuy;
            return this;
        }

        public BigDecimal getCashBuy() {
            return cashBuy;
        }

        public BocRate setCashBuy(BigDecimal cashBuy) {
            this.cashBuy = cashBuy;
            return this;
        }

        public BigDecimal getExchangeSale() {
            return exchangeSale;
        }

        public BocRate setExchangeSale(BigDecimal exchangeSale) {
            this.exchangeSale = exchangeSale;
            return this;
        }

        public BigDecimal getCashSale() {
            return cashSale;
        }

        public BocRate setCashSale(BigDecimal cashSale) {
            this.cashSale = cashSale;
            return this;
        }

        public BigDecimal getBocDisPrice() {
            return bocDisPrice;
        }

        public BocRate setBocDisPrice(BigDecimal bocDisPrice) {
            this.bocDisPrice = bocDisPrice;
            return this;
        }

        public String getRelesDate() {
            return relesDate;
        }

        public BocRate setRelesDate(String relesDate) {
            this.relesDate = relesDate;
            return this;
        }

        public String getRelesTime() {
            return relesTime;
        }

        public BocRate setRelesTime(String relesTime) {
            this.relesTime = relesTime;
            return this;
        }

        @Override
        public String toString() {
            return "BocRate{" +
                    "currencyName='" + currencyName + '\'' +
                    ", exchangeBuy=" + exchangeBuy +
                    ", cashBuy=" + cashBuy +
                    ", exchangeSale=" + exchangeSale +
                    ", cashSale=" + cashSale +
                    ", bocDisPrice=" + bocDisPrice +
                    ", relesDate='" + relesDate + '\'' +
                    ", relesTime='" + relesTime + '\'' +
                    ", currency='" + currency + '\'' +
                    '}';
        }

        public String bocFormatString() {
            return  currencyName + ","+
                    exchangeBuy  + ","+
                    cashBuy + ","+
                    exchangeSale +","+
                    cashSale +","+
                     bocDisPrice +","+
                     relesDate +" "+
                     relesTime +","+
                      currency +"\n";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {return true;};

            if (o == null || getClass() != o.getClass()) {return false;};

            BocRate bocRate = (BocRate) o;

            return new EqualsBuilder()
                    .append(currencyName, bocRate.currencyName)
                    .append(currency, bocRate.currency)
                    .append(exchangeBuy, bocRate.exchangeBuy)
                    .append(cashBuy, bocRate.cashBuy)
                    .append(exchangeSale, bocRate.exchangeSale)
                    .append(cashSale, bocRate.cashSale)
                    .append(bocDisPrice, bocRate.bocDisPrice)
                    .append(relesDate, bocRate.relesDate)
                    .append(relesTime, bocRate.relesTime)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(currencyName)
                    .append(currency)
                    .append(exchangeBuy)
                    .append(cashBuy)
                    .append(exchangeSale)
                    .append(cashSale)
                    .append(bocDisPrice)
                    .append(relesDate)
                    .append(relesTime)
                    .toHashCode();
        }
    }

    /**
     * 搜索字符串
     * @param regex
     * @param source
     * @return
     */
    public static String getMatcher(String regex, String source) {
        String result = "";
        if(regex==null || source==null){
            System.err.println("regex error");
            return result;}
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            result = matcher.group(1);
        }
        return result;
    }

    private static final Map<String,String> CODECURRENCY = new HashMap(){{
        put("GBP","1314");
        put("gbp","1314");
        put("UK","1314");
        put("uk","1314");
        put("HKD","1315");
        put("hkd","1315");
        put("HK","1315");
        put("hk","1315");
        put("USD","1316");
        put("usd","1316");
        put("USA","1316");
        put("usa","1316");
        put("US","1316");
        put("us","1316");
        put("CHF","1317");
        put("chf","1317");
        put("FS","1317");
        put("Fr","1317");
        put("DM","1318");
        put("dm","1318");
        put("FF","1319");
        put("ff","1319");
        put("SGD","1375");
        put("sgd","1375");
        put("SEK","1320");
        put("sek","1320");
        put("DKK","1321");
        put("dkk","1321");
        put("NOK","1322");
        put("nok","1322");
        put("kr","1331");
        put("JPY","1323");
        put("jpy","1323");
        put("JP","1323");
        put("jp","1323");
        put("Yen","1323");
        put("CAD","1324");
        put("cad","1324");
        put("CA","1324");
        put("ca","1324");
        put("AUD","1325");
        put("aud","1325");
        put("AU","1325");
        put("au","1325");
        put("EUR","1326");
        put("eur","1326");
        put("EU","1326");
        put("eu","1326");
        put("MOP","1327");
        put("mop","1327");
        put("PHP","1328");
        put("php","1328");
        put("Peso","1328");
        put("THB","1329");
        put("thb","1329");
        put("Thai","1329");
        put("NZD","1330");
        put("nzd","1330");
        put("Kiwi","1330");
        put("KRW","1331");
        put("krw","1331");
        put("KR","1331");
        put("RUB","1843");
        put("rub","1843");
        put("RU","1843");
        put("ru","1843");
        put("MYR","2890");
        put("myr","2890");
        put("Sen","2890");
        put("TWD","2895");
        put("twd","2895");
        put("NTD","2895");
        put("ntd","2895");
        put("TW","2895");
        put("tw","2895");
        put("ESP","1370");
        put("esp","1370");
        put("ITL","1371");
        put("itl","1371");
        put("NLG","1372");
        put("nlg","1372");
        put("BEF","1373");
        put("bef","1373");
        put("FIM","1374");
        put("fim","1374");
        put("IDR","3030");
        put("idr","3030");
        put("BRL","3253");
        put("brl","3253");
        put("AED","3899");
        put("aed","3899");
        put("DH","3899");
        put("Dhs","3899");
        put("INR","3900");
        put("inr","3900");
        put("ZAR","3901");
        put("zar","3901");
        put("SAR","4418");
        put("sar","4418");
        put("TRY","4560");
        put("try","4560");
        put("YTL","4560");
        put("ytl","4560");
    }};
}
