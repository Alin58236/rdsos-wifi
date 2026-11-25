package com.rdsos.wifianalyzer.service.sniffer;

import com.rdsos.wifianalyzer.model.sniffer.DecodedPacket;
import com.rdsos.wifianalyzer.model.sniffer.StoredPacket;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;


@Component
public class PacketStore {

    private static final int MAX_CAPACITY = 5000;

    private final ArrayDeque<StoredPacket> packets = new ArrayDeque<>();
    private long nextId = 1;

    public synchronized long add(DecodedPacket packet){

        long id = nextId++;
        packets.addLast(new StoredPacket(id, packet));

        if(packets.size() > MAX_CAPACITY){
            packets.removeFirst();
        }

        return id;
    }

    public synchronized StoredPacket get(long id){

        for(StoredPacket stored : packets){
            if(stored.getId() == id){
                return stored;
            }
        }
        return null;
    }

    public synchronized List<StoredPacket> after(long lastId, int max){

        List<StoredPacket> result = new ArrayList<>();

        for(StoredPacket stored : packets){
            if(result.size() == max){
                break;
            }
            if(stored.getId() > lastId){
                result.add(stored);
            }
        }
        return result;
    }
}
