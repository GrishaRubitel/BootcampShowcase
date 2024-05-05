package com.bootcamp_proj.bootcampproj.standalone_services;

import com.bootcamp_proj.bootcampproj.additional_classes.BrtTransaction;
import com.bootcamp_proj.bootcampproj.additional_classes.MonthStack;
import com.bootcamp_proj.bootcampproj.psql_brt_abonents.BrtAbonents;
import com.bootcamp_proj.bootcampproj.psql_brt_abonents.BrtAbonentsService;
import com.bootcamp_proj.bootcampproj.psql_tariffs_stats.TariffStats;
import com.bootcamp_proj.bootcampproj.psql_tariffs_stats.TariffStatsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@EnableAsync
public class BrtHandler {
    private static final String BOOTCAMP_PROJ_GROUP = "bootcamp-proj-group";
    private static final String DATA_TOPIC = "data-topic";
    private static final String PART_ZERO = "0";
    private static final String HOST = "http://nginx_hrs:";
    private static final String SINGLE_PAY_PARAM = "/api/hrs-handler/single-pay?param=";
    private static final String MONTHLY_PAY_PARAM = "/api/hrs-handler/monthly-pay?param=";
    private static final String PORT = "9999";

    private static WeakHashMap<Long, BrtAbonents> brtAbonentsMap = new WeakHashMap<>();
    private static LinkedList<String> monthlyTariffs;
    private static MonthStack monthHolder;
    private static RestTemplate restTemplate;

    @Autowired
    TariffStatsService tariffStatsService;
    @Autowired
    BrtAbonentsService brtAbonentsService;

    @PostConstruct
    private void initializeStack() {
        monthHolder = fillStack();
    }

    @KafkaListener(topics = DATA_TOPIC, groupId = BOOTCAMP_PROJ_GROUP, topicPartitions = {
            @TopicPartition(topic = DATA_TOPIC, partitions = PART_ZERO)
    })
    private void consumeFromDataTopic(String message) {
        System.out.println("BRT-D-P0 from BRT: \n" + message);
        cdrDataHandler(message);
    }

    protected void cdrDataHandler(String message) {
        selectAllTariffs();
        selectAllAbonents();
        restTemplate = new RestTemplate();

        try (BufferedReader br = new BufferedReader(new StringReader(message))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
                BrtTransaction temp = new BrtTransaction(line);

                checkMonthChangement(temp.getUnixEnd());

                if (checkAbonent(temp.getMsisdn())) {
                    temp.setTariffId(brtAbonentsMap.get(temp.getMsisdn()).getTariffId());
                    temp.setInNet(checkAbonent(temp.getMsisdnTo()));
                    proceedPayment(sendGetToHrs(temp.toJson(), SINGLE_PAY_PARAM));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String sendGetToHrs(String temp, String urlParam) {
        String url = HOST + PORT + urlParam + encodeParams(temp);
        System.out.println(url);
        String response;
        try {
            response = restTemplate.getForObject(url, String.class);
            System.out.println("BRT API Callback: \n" + response);
            return response;
        } catch (Exception e) {
            System.out.println("BRT API: Exception happened \n" + e.getMessage());
        }
        return null;
    }

    private void checkMonthChangement(int record) {
        if (monthHolder.checkTop(record)) {
            for (BrtAbonents abonent : brtAbonentsMap.values()) {
                if (monthlyTariffs.contains(abonent.getTariffId())) {
                    sendGetToHrs(abonent.toJson(), MONTHLY_PAY_PARAM);
                }
            }
        }
    }

    private void proceedPayment(String cheque) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(cheque);
            long msisdn = jsonNode.get("msisdn").asLong();
            double price = jsonNode.get("callCost").asDouble();

            BrtAbonents abonent = brtAbonentsMap.get(msisdn);
            if (price != 0) {
                abonent.decreaseMoneyBalance(price);
                brtAbonentsService.commitUserTransaction(abonent);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean checkAbonent(long rec) {
        if (brtAbonentsMap.containsKey(rec)) {
            return true;
        } else {
            BrtAbonents temp = brtAbonentsService.findById(rec);
            if (temp == null) {
                return false;
            }
            brtAbonentsMap.put(temp.getMsisdn(), temp);
            return true;
        }
    }

    private void selectAllAbonents() {
        brtAbonentsMap = new WeakHashMap<>();

        for (BrtAbonents elem : brtAbonentsService.findAll()) {
            brtAbonentsMap.put(elem.getMsisdn(), elem);
        }
    }

    private void selectAllTariffs(){
        monthlyTariffs = new LinkedList<>();

        for (TariffStats elem : tariffStatsService.getAllTariffStats()) {
            if (elem.getPrice_of_period() != 0) {
                monthlyTariffs.add(elem.getTariff_id());
            }
        }
    }

    private String callSinglePayAPI(String param) {
        String url = HOST + PORT + SINGLE_PAY_PARAM + param;
        return restTemplate.getForObject(url, String.class);
    }

    private static String encodeParams(String params) {
        String encodedParams = "";
        try {
            encodedParams = URLEncoder.encode(params, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return encodedParams;
    }

    private MonthStack fillStack() {
        MonthStack monthHolder = new MonthStack();

        monthHolder.push(1701388800);
        monthHolder.push(1698796800);
        monthHolder.push(1696118400);
        monthHolder.push(1693526400);
        monthHolder.push(1690848000);
        monthHolder.push(1688169600);
        monthHolder.push(1685577600);
        monthHolder.push(1682899200);
        monthHolder.push(1680307200);
        monthHolder.push(1677628800);
        monthHolder.push(1675209600);
        monthHolder.push(1672571200);

        return monthHolder;
    }
}