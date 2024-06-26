package com.bootcamp_proj.bootcampproj.psql_brt_abonents;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * ORM сервис для выполнения запросов к таблице "users" базы данных оператора "Ромашки"
 */
@Service
public class BrtAbonentsService {
    @Autowired
    private BrtAbonentsRepository brtAbonentsRepository;

    public BrtAbonentsService() {}

    public Iterable<BrtAbonents> findAll() {
        return brtAbonentsRepository.findAll();
    }

    public boolean findInjection(long msisdn) {
        Iterable<BrtAbonents> brtAbonents = brtAbonentsRepository.findAll();
        for (BrtAbonents abonent : brtAbonents) {
            if (abonent.getMsisdn() == msisdn) {
                return true;
            }
        }
        return false;
    }

    public Iterable<BrtAbonents> selectAllAbonents() {
        return brtAbonentsRepository.findAll();
    }

    public String getTariffId(long msisdn) {
        Iterable<BrtAbonents> brtAbonent = brtAbonentsRepository.findAllById(Collections.singleton(msisdn));
        return brtAbonent.iterator().next().getTariffId();
    }

    public BrtAbonents findById(long msisdn) {
        return brtAbonentsRepository.findById(msisdn).orElse(null);
    }

    public void commitUserTransaction(BrtAbonents abonent) {
        brtAbonentsRepository.save(abonent);
    }
}
