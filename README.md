# OneNET 远程开关小灯

---

> 代码托管在 https://github.com/CQCET-IOT/remote-light-control

中移物联网 NB 开发板有一个光照传感器，有一个可以远程控制开关的小灯。周老师某天提出，能不能通过判断光照传感器的值来自动控制小灯的开和关呢？我仔细研究了一下，发现通过 OneNET 提供的触发器是可行的。

> **注意**：本程序因为没有固化 apiKey 和 IMEI，因此任何人只要在 OneNET 构造触发器，按照规则组装推送的 URL，则都可以使用。但实际的应用程序不可能将 apiKey 和 IMEI 作为 URL 参数使用，也不能对外暴露，因此这个项目仅仅作为培训演示使用。


## 开发环境
1. jdk 1.8
2. maven 3.0+，构建工具
3. IntelliJ IDEA，IDE
4. git，版本维护工具

## 控制小灯开关的 API 

[LwM2M ﻿即时命令-写设备资源](https://open.iot.10086.cn/doc/book/application-develop/api/LwM2M/5%E5%8D%B3%E6%97%B6%E5%91%BD%E4%BB%A4-%E5%86%99%E8%AE%BE%E5%A4%87%E8%B5%84%E6%BA%90.html) 这一篇文档说得很清楚，要想开关小灯，需要构造一些参数：

- 请求方式：POST
- URL：http://api.heclouds.com/nbiot
- URL 参数：imei, obj_id, obj_inst_id, mode
- Header 参数：api-key, Content-Type
- 请求 Body 参数：data

可以先在 POSTMAN 中测试 API，测试通过后再去编程：

![image_1dg88vpft152l1vtrb9817ca1g151t.png-82.8kB][1]

![image_1dg891hi2q2n1sp41d4q1u8f15kf2a.png-93.7kB][2]

## SDK

中移物联网开发了一个 LwM2M SDK，该 SDK 专门针对 NB 设备对 HTTP API 进行了封装，可以创建设备、订阅资源、读资源、写资源。控制 LED 灯就是读资源。

![image_1dg82ljtcouq1e6d1lvs1q8u1o5s13.png-126.2kB][3]

> LwM2M SDK 其实并不完整，比如读取数据点，就没法使用这个 SDK 来完成，反而需要用上图最后一个 SDK 来实现。不过本例使用 LwM2M SDK 即可。

克隆该项目，得到源代码，供后面集成用。

## URL 设计

OneNET 要想实现推送，则推送的地址需要位于公网上，内网地址很难穿透。因此，本应用程序需要运行在某个云端的服务器上，服务器必须具备公网 IP 地址或者公网域名。

OneNET 触发器允许的 URL 长度不能超过 64，所以在实现时必须尽可能地减少 URL 长度。我们来分析一下 URL 的内容：

|项|长度|
|-|-
|http://|7
|公网IP地址(或公网域名)端口|不定
|控制指令|至少1位，*o* 表示打开，*c* 表示关闭
|apiKey|28
|imei|15
|URL分隔符|若干

可以看出，固定项已经占据了至少 51 个字符，因此公网 IP 地址端口和 URL 分隔符的长度不能超过 13 个字符。

首先抛弃的是下述设计，因为携带了端口号和参数字段，导致长度太长：

```
# 长度 88
http://123.144.xx.xxx:8099/open?apiKey=9UExxxxxxxxxxxxxxxxxxxxxkos=&imei=8858xxxxxxxxxxx
```

其次看看这种设计：

```
# 长度 66
http://www.xxxx.top/o/9UExxxxxxxxxxxxxxxxxxxxxkos=/8858xxxxxxxxxxx
```
> 长度还是不能满足要求。此时，我想到了短网址。找一个短地址第三方服务，生成原网址的短地址，在浏览器中，短网址是可以跳转到原网址的，可是，消息推送至短网址，并不能跳转到原网址，亲测。

现在剩下的唯一的办法是把路径中的 */* 符号去掉，长度刚好为 64。所以，最终设计为：

```
# 打开小灯，长度 64，规则：http://www.xxxx.top/o+apiKey+IMEI
http://www.xxxx.top/o9UExxxxxxxxxxxxxxxxxxxxxkos=8858xxxxxxxxxxx

# 关闭小灯，长度 64，规则：http://www.xxxx.top/c+apiKey+IMEI
http://www.xxxx.top/c9UExxxxxxxxxxxxxxxxxxxxxkos=8858xxxxxxxxxxx
```
> **注意**：如果你的公网域名长度大于12，那么此方法还是无法实现，另想他法吧。我这个正好长度为12。

> **注意**：对外暴露 apiKey 和 IMEI 是很危险的。此例为了让所有人都可以访问云服务，不得已而为之。


## 新建 Web 项目

使用 IDEA 新建一个 SpringBoot Web 项目，名字空间为 *cmcciot.onenet.nbapi.sdk*，与 SDK 相同。在 *pom.xml* 中导入 SDK 的版本定义和依赖：

```
<properties>
	<java.version>1.8</java.version>
	<json.version>20180130</json.version>
	<slf4j.version>1.7.20</slf4j.version>
	<logback.version>1.2.3</logback.version>
	<okhttp.version>3.5.0</okhttp.version>
	<yaml.version>1.10</yaml.version>
	<commons.lang3.version>3.4</commons.lang3.version>
	<commons.collections.version>3.2.1</commons.collections.version>
</properties>
```

```
<dependency>
	<groupId>org.json</groupId>
	<artifactId>json</artifactId>
	<version>${json.version}</version>
</dependency>
<dependency>
	<groupId>org.apache.commons</groupId>
	<artifactId>commons-lang3</artifactId>
	<version>${commons.lang3.version}</version>
</dependency>
<dependency>
	<groupId>commons-collections</groupId>
	<artifactId>commons-collections</artifactId>
	<version>${commons.collections.version}</version>
</dependency>
<dependency>
	<groupId>org.slf4j</groupId>
	<artifactId>slf4j-api</artifactId>
	<version>${slf4j.version}</version>
</dependency>
<dependency>
	<groupId>ch.qos.logback</groupId>
	<artifactId>logback-classic</artifactId>
	<version>${logback.version}</version>
</dependency>
<dependency>
	<groupId>com.squareup.okhttp3</groupId>
	<artifactId>okhttp</artifactId>
	<version>${okhttp.version}</version>
</dependency>
```

将 SDK 的代码导入到工程中，并将 *config.properties* 文件拷贝到 *resources* 目录。然后新建 *controller.LightController* 类，在类中实现：

```
@RestController
public class LightController {
    private static final Logger LOGGER = LoggerFactory.getLogger(LightController.class);

    @RequestMapping("/{param}")
    public String control(@PathVariable String param) {
        try {
            String command = param.substring(0, 1);
            String apiKey = param.substring(1, 29);
            String imei = param.substring(29, param.length());

            System.out.println("apiKey: " + apiKey);
            System.out.println("IMEI: " + imei);

            if (command.equals("o")) {
                System.out.println("OPEN COMMAND");
                switchLight(apiKey, imei, true);
            } else if (command.equals("c")) {
                System.out.println("CLOSE COMMAND");
                switchLight(apiKey, imei, false);
            }
        }
        catch (Exception e) {
            System.out.println("Input Param Error: " + param + "; Detail: " + e.toString());
        } finally {
            return "light";
        }
    }

    /**
     * 开关小灯
     * @param apiKey    产品Master apiKey
     * @param imei      设备IMEI
     * @param command   false-关闭；true-打开
     */
    public void switchLight(String apiKey, String imei, boolean command) {
        Integer objId = 3311;
        Integer objInstId = 0;
        Integer writeResId = 5850;
        Integer writeMode = 1;

        // Write Resource
        WriteOpe writeOpe = new WriteOpe(apiKey);
        Write write = new Write(imei, objId, objInstId, writeMode);
        JSONArray jsonArray = new JSONArray();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("res_id", writeResId);
        jsonObject.put("val", command);
        jsonArray.put(jsonObject);
        JSONObject data = new JSONObject();
        data.put("data", jsonArray);
        LOGGER.info(writeOpe.operation(write, data).toString());
    }
}
```

最后，在 *pom.xml* 中添加插件，以便整个工程可以打包成独立运行的 jar 包：

```
<!-- 打包成可运行的Jar包 -->
<plugin>
	<groupId>org.apache.maven.plugins</groupId>
	<artifactId>maven-jar-plugin</artifactId>
	<version>2.5</version>
</plugin>
```

## 部署

使用 *mvn:package* 打包，将生成的 *remote-light-control-0.0.1-SNAPSHOT.jar* 拷贝到服务器上。

在服务器上安装 JDK1.8，然后运行：

```
java -jar remote-light-control-0.0.1-SNAPSHOT.jar --server.port=80
```

这样，在 *www.xxxx.top* 服务器上，Web 程序监听 80 端口。这样端口地址也不用写了，否则 URL 又要超过长度。


## 使用

在 OneNET 上添加 *LightOpen* 和 *LightClose* 触发器：

![image_1dg89i3ad2t0m54epg19qn3u.png-91.8kB][4]

触发器推送 URL 遵循以下规则：

```
# 打开小灯，长度 64，规则：http://www.xxxx.top/o+apiKey+IMEI
http://www.xxxx.top/o9UExxxxxxxxxxxxxxxxxxxxxkos=8858xxxxxxxxxxx

# 关闭小灯，长度 64，规则：http://www.xxxx.top/c+apiKey+IMEI
http://www.xxxx.top/c9UExxxxxxxxxxxxxxxxxxxxxkos=8858xxxxxxxxxxx
```

遮住光照传感器，保持 30 秒以上，则触发器触发 *LightOpen* 规则，云服务调用打开命令，将灯远程打开；将光照传感器至于强光下，保持 30 秒以上，则触发器触发 *LightClose* 规则，云服务调用关闭命令，将灯远程关闭。


  [1]: http://static.zybuluo.com/morgen/9kpytohh0repuei796jmlhzn/image_1dg88vpft152l1vtrb9817ca1g151t.png
  [2]: http://static.zybuluo.com/morgen/z5abplk3ajc7iq7wfxzzg4bk/image_1dg891hi2q2n1sp41d4q1u8f15kf2a.png
  [3]: http://static.zybuluo.com/morgen/3qygz5v6c73p7z9klvwfd12u/image_1dg82ljtcouq1e6d1lvs1q8u1o5s13.png
  [4]: http://static.zybuluo.com/morgen/418v7sykaby1okzcqxu2navn/image_1dg89i3ad2t0m54epg19qn3u.png
