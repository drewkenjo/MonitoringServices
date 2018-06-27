/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.clas12.mon.forward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataBank;
import org.jlab.clas12.mon.MonitoringEngine;
import org.jlab.detector.base.DetectorType;

/**
 *
 * @author kenjo
 */
public class NumberOfNeutrals extends MonitoringEngine {

    private Map<String, AtomicInteger> nneutrals = new ConcurrentHashMap<>();
    private Map<String, AtomicInteger> ntriggers = new ConcurrentHashMap<>();
    AtomicInteger nprocessed = new AtomicInteger(0);
    private final int nintegration = 10000;

    /**
     * Constructor.
     */
    public NumberOfNeutrals() {
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        if (event.hasBank("REC::Particle") && event.hasBank("REC::Calorimeter") && event.hasBank("RUN::config")) {
            DataBank runbank = event.getBank("RUN::config");
            DataBank pbank = event.getBank("REC::Particle");
            DataBank calbank = event.getBank("REC::Calorimeter");

            int nrows = pbank.rows();
            int[] sector = new int[nrows];
            for (int ical = 0; ical < calbank.rows(); ical++) {
			 int idet = calbank.getByte("detector", ical);
			 if(idet == DetectorType.ECAL.getDetectorId()){
                     int ilay = calbank.getByte("layer", ical);
                     if (ilay == 1 || ilay == 4 || ilay == 7) {
		                int pindex = calbank.getShort("pindex", ical);
     		           sector[pindex] = calbank.getByte("sector", ical);
				 }
			 }
            }

            String keys = runbank.getInt("run", 0) + ",0,";
            ntriggers.computeIfAbsent(keys, k -> new AtomicInteger(0)).incrementAndGet();

            for (int ipart = 0; ipart < nrows; ipart++) {
                int charge= pbank.getByte("charge", ipart);
                if (charge==0 && sector[ipart] > 0) {
            		nneutrals.computeIfAbsent(keys + sector[ipart], k -> new AtomicInteger(0)).incrementAndGet();
                }
            }
        }

        //we lose the last chunk of events. Need to ask Vardan how to check if it's the last event (problematic in parallel mode)
        if (nprocessed.getAndIncrement() % nintegration == 0) {

            List<Map<String, String>> nrates = ntriggers.keySet().stream()
                    .map(key -> {
                        Map<String, String> nneut = new HashMap<>();
                        if (ntriggers.containsKey(key) && ntriggers.get(key).get() > 100) {
					   String[] keys = key.split(",");
                            nneut.put("run", keys[0]);
                            nneut.put("time", keys[1]);
                            float denom = ntriggers.get(key).get();
                            for (int isec = 1; isec <= 6; isec++) {
                                if (nneutrals.containsKey(key+isec)) {
                                    nneut.put("nneut" + isec, Float.toString(nneutrals.get(key+isec).get() / denom));
                                }
                            }
                        }
                        return nneut;
                    })
                    .filter(nneut -> nneut.keySet().size() > 2)
                    .collect(Collectors.toList());

//            nrates.stream().forEach(x->x.values().forEach(System.out::println));

            submit("monneut", nrates);
        }
        return true;
    }

}
