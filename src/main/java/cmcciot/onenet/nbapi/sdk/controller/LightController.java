package cmcciot.onenet.nbapi.sdk.controller;

import cmcciot.onenet.nbapi.sdk.api.online.WriteOpe;
import cmcciot.onenet.nbapi.sdk.entity.Write;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by thinker on 2019/7/20.
 */
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
     * API手册：https://open.iot.10086.cn/doc/book/application-develop/api/LwM2M/5%E5%8D%B3%E6%97%B6%E5%91%BD%E4%BB%A4-%E5%86%99%E8%AE%BE%E5%A4%87%E8%B5%84%E6%BA%90.html
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
