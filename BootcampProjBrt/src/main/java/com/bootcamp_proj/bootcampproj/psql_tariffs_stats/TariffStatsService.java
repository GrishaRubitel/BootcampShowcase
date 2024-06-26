package com.bootcamp_proj.bootcampproj.psql_tariffs_stats;

import com.bootcamp_proj.bootcampproj.psql_transactions.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * ORM сервис для выполнения запросов к таблице "tariffs" базы данных генератора звонков
 */
@Service
public class TariffStatsService {
    @Autowired
    TariffStatsRepository tariffStatsRepository;

    public TariffStatsService() {}

    public double getPriceIncoming(String msisdn, TariffStats stats) {
        return stats.getPrice_incoming_calls();
    }

    public double getPriceOutcoming(String msisdn, TariffStats stats) {
        return stats.getPrice_outcoming_calls();
    }

    public double getPriceOutcomingCamo(String msisdn, TariffStats stats) {
        return stats.getPrice_outcoming_calls_camo();
    }

    public TariffStats getTariffStats(String msisdn) {
        return tariffStatsRepository.findById(msisdn).orElse(null);
    }

    public Iterable<TariffStats> getAllTariffStats() {
        return tariffStatsRepository.findAll();
    }
}
