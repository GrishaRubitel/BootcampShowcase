package com.bootcamp_proj.bootcampproj.standalone_services;

import com.bootcamp_proj.bootcampproj.additional_classes.HrsCallbackRecord;
import com.bootcamp_proj.bootcampproj.additional_classes.HrsCallbackRecord;
import com.bootcamp_proj.bootcampproj.additional_classes.HrsTransaction;
import com.bootcamp_proj.bootcampproj.psql_brt_abonents.BrtAbonentsService;
import com.bootcamp_proj.bootcampproj.psql_hrs_user_minutes.UserMinutes;
import com.bootcamp_proj.bootcampproj.psql_hrs_user_minutes.UserMinutesService;
import com.bootcamp_proj.bootcampproj.psql_tariffs_stats.TariffStats;
import com.bootcamp_proj.bootcampproj.psql_tariffs_stats.TariffStatsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

@RestController
@Service
@RequestMapping("/api/hrs")
public class HrsHandler {
    private static final String IN_CALL_TYPE_CODE = "02";
    private static final int ZERO = 0;
    private static final String TARIFF_BY_DEFAULT = "11";
    private WeakHashMap<String, TariffStats> tariffStats;
    private Map<Long, UserMinutes> usersWithTariff = new HashMap<>();

    @PostConstruct
    private void initializeMap() {
        tariffStats = uploadTariff();
    }

    @Autowired
    BrtAbonentsService brtAbonentsService;
    @Autowired
    TariffStatsService tariffStatsService;
    @Autowired
    private UserMinutesService userMinutesService;

    /**
     * Api для обработки звонков
     * @param param Информация о звонке
     * @return Номер телфона и стоимость звонка
     */
    @GetMapping("/single-pay")
    @ResponseStatus(HttpStatus.OK)
    public String singlePay(@RequestParam String param) {
        return tariffDispatch(decodeParam(param));
    }

    /**
     * Api для обработки ежемесячных списаний
     * @param param Информация об абоненте
     * @return Номер телефона и стоимость звока
     */
    @GetMapping("/monthly-pay")
    @ResponseStatus(HttpStatus.OK)
    public String monthlyPay(@RequestParam String param) {
        return payDay(decodeParam(param));
    }

    /**
     * Метод для конкретной обработки ежемесячных списаний
     * @param param Информация об абоненте
     * @return Номер телефона и стоимость звонка
     */
    private String payDay(String param) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(param);
            long msisdn = jsonNode.get("msisdn").asLong();
            String tariff = jsonNode.get("tariffId").asText();

            UserMinutes tempUser = checkUserContainment(msisdn, tariff);
            tempUser.zeroAllMinutes();
            userMinutesService.saveUserMinutes(tempUser);

            double price = tariffStats.get(tempUser.getTariff_id()).getPrice_of_period();

            HrsCallbackRecord hrsCallbackRecord = new HrsCallbackRecord(tempUser.getMsisdn(), price);
            return hrsCallbackRecord.toJson();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return param;
    }

    /**
     * Распределение звонков по типу "Есть минуты на тарифе или нет"
     * @param message Информация о звонке
     * @return Номер и стоимость
     */
    private String tariffDispatch(String message) {
        HrsTransaction record = new HrsTransaction(message);
        if (tariffStats.get(record.getTariffId()).getPrice_of_period() == 0) {
            return noMinutesTariff(record, record.getTariffId());
        }
        return withMinutesTariff(record);
    }

    /**
     * Обработки тарифов без количества месячных минут
     * @param record Информация о звонке
     * @param tariff Тариф абонента. Это может быть как тариф абонента, так и альтернативный тариф "без минут"
     * @return Номер и стоимость
     */
    private String noMinutesTariff(HrsTransaction record, String tariff) {
        double timeSpent = Math.ceil(record.countCallLength() / 60);

        TariffStats tStats = tariffStats.get(tariff);
        double price;

        if (record.getCallId().equals(IN_CALL_TYPE_CODE)) {
            price = tStats.getPrice_incoming_calls();
        } else {
            if (record.getInNet()) {
                price = tStats.getPrice_outcoming_calls_camo();
            } else {
                price = tStats.getPrice_outcoming_calls();
            }
        }
        double sum = timeSpent * price;

        HrsCallbackRecord hrsCallbackRecord = new HrsCallbackRecord(record.getMsisdn(), sum);
        return hrsCallbackRecord.toJson();
    }

    /**
     * Обработки тарифов с количеством месячных минут
     * @param record Информация о звонке
     * @return Номер и стоимость
     */
    private String withMinutesTariff(HrsTransaction record) {
        double timeSpent = Math.ceil(record.countCallLength() / 60);

        TariffStats tStats = tariffStats.get(record.getTariffId());

        UserMinutes userMinutes = checkUserContainment(record.getMsisdn(), record.getTariffId());

        String returnVal;

        if (userMinutes.getUsed_minutes_in() + timeSpent <= tStats.getNum_of_minutes()) {
            userMinutes.increaseMinutes((int) timeSpent);
            returnVal = new HrsCallbackRecord(record.getMsisdn(), 0).toJson();
        } else {
            userMinutes.setAllMinutes(tStats.getNum_of_minutes());
            returnVal = noMinutesTariff(record, TARIFF_BY_DEFAULT);
        }

        userMinutesService.saveUserMinutes(userMinutes);
        return returnVal;
    }

    /**
     * Проверка на включение абонента в кеш
     * @param msisdn Номер телефона
     * @param tariff Тариф абонента
     * @return
     */
    private UserMinutes checkUserContainment(long msisdn, String tariff) {
        if (!usersWithTariff.containsKey(msisdn)) {
            UserMinutes temp = new UserMinutes(msisdn, tariff, ZERO, ZERO);
            usersWithTariff.put(temp.getMsisdn(), temp);
            return temp;
        } else {
            return usersWithTariff.get(msisdn);
        }
    }

    /**
     * Извлечение всех тарифов из базы данных BRT
     * @return Мапу слабых связей со всеми тарифами (так надо чтобы GC не удалил ничего)
     */
    private WeakHashMap<String, TariffStats> uploadTariff() {
        WeakHashMap<String, TariffStats> tS = new WeakHashMap();
        for (TariffStats elem : tariffStatsService.getAllTariffStats()) {
            tS.put(elem.getTariff_id(), elem);
        }
        return tS;
    }

    /**
     * Декодирование информации из URL запроса
     * @param encodedString Декодируемая строка
     * @return Декодированная строка
     */
    private static String decodeParam(String encodedString) {
        String decodedString = "";
        try {
            decodedString = URLDecoder.decode(encodedString, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return decodedString;
    }
}
