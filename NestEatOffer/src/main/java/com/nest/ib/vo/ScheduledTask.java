package com.nest.ib.vo;

import com.nest.ib.service.EatOfferAndTransactionService;

import com.nest.ib.service.OfferThreeDataService;
import com.nest.ib.service.serviceImpl.EatOfferAndTransactionServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * ClassName:
 * Description:
 */
@Component
public class ScheduledTask {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(50);
        return taskScheduler;
    }

    @Autowired
    private EatOfferAndTransactionService eatOfferAndTransactionService;


    /**
     * Order quote :3 seconds
     */
    @Scheduled(fixedDelay = 3 * 1000)
    public void eatOfferContract() {
        eatOfferAndTransactionService.eatOffer();
    }

    /**
     * Retrieve assets: 2 minutes
     */
    @Scheduled(fixedDelay = 2 * 60 * 1000)
    public void retrieveAssets() {
        eatOfferAndTransactionService.retrieveAssets();
    }


    /**
     * To exchange quoted assets: 10 seconds
     */
    @Scheduled(fixedDelay = 10 * 1000)
    public void exchangeBuyAndSell() {
        eatOfferAndTransactionService.exchangeBuyAndSell();
    }
}
