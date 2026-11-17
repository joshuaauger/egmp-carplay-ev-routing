package org.egmp.carplay.ev.app;
// SPDX-License-Identifier: GPL-3.0-or-later
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** CarPlayStartSession (iAP2 0x4301, setting carplay_start_session), as LIVI sends it (livi-runtime
 * bringup.rs carplay_start_session, iap2-csm messages/car_play.rs): the car's answer to the phone's
 * CarPlayAvailability (0x4300). It names the Wi-Fi, the AirPlay receiver and the CarPlay theme asset
 * (identifier and version, with its SDK version), which the stock wireless start (0x4E0D, then the
 * phone asking for Wi-Fi credentials) never tells the phone. The native side sends these bytes. */
final class StartSession {
    static final int MSG=0x4301,AIRPLAY_PORT=7000;
    static final String DIPO_INFO="/data/data/com.daudio.app.dipo/files/cpc-airplay",HOSTAPD="/data/misc/wifi/hostapd.conf",HOTSPOT="wlan0";
    private StartSession(){}

    /** the whole message: 40 40, u16 length, 43 01, parameters (u16 length, u16 id, payload) */
    static byte[] encode(String ssid,String passphrase,int channel,List<String> ips,int security,int port,
                         String deviceId,String publicKey,String sourceVersion,String sdkVersion,String assetId,long assetVersion){
        ByteArrayOutputStream wireless=new ByteArrayOutputStream();
        if(ssid!=null)param(wireless,0,str(ssid));
        if(passphrase!=null)param(wireless,1,str(passphrase));
        if(channel>=0)param(wireless,2,new byte[]{(byte)channel});
        ByteArrayOutputStream list=new ByteArrayOutputStream();
        for(String ip:ips){byte[] b=str(ip);list.write(b,0,b.length);}
        param(wireless,3,list.toByteArray());
        if(security>=0)param(wireless,4,new byte[]{(byte)security});
        ByteArrayOutputStream p=new ByteArrayOutputStream();
        param(p,1,wireless.toByteArray());
        if(port>=0)param(p,2,u32(port));
        if(deviceId!=null)param(p,3,str(deviceId));
        if(publicKey!=null)param(p,4,str(publicKey));
        if(sourceVersion!=null)param(p,5,str(sourceVersion));
        if(sdkVersion!=null)param(p,6,str(sdkVersion));
        if(assetId!=null){
            ByteArrayOutputStream asset=new ByteArrayOutputStream();
            param(asset,0,str(assetId));param(asset,1,u32(assetVersion));
            param(p,7,asset.toByteArray());
        }
        byte[] body=p.toByteArray();int n=body.length+6;
        byte[] out=new byte[n];
        out[0]=0x40;out[1]=0x40;out[2]=(byte)(n>>8);out[3]=(byte)n;out[4]=(byte)(MSG>>8);out[5]=(byte)MSG;
        System.arraycopy(body,0,out,6,body.length);
        return out;
    }
    private static void param(ByteArrayOutputStream out,int id,byte[] payload){
        int n=payload.length+4;
        out.write(n>>8);out.write(n);out.write(id>>8);out.write(id);out.write(payload,0,payload.length);
    }
    private static byte[] str(String s){
        try{byte[] b=s.getBytes("UTF-8");byte[] z=new byte[b.length+1];System.arraycopy(b,0,z,0,b.length);return z;}
        catch(java.io.UnsupportedEncodingException e){throw new IllegalStateException(e);}
    }
    private static byte[] u32(long v){return new byte[]{(byte)(v>>24),(byte)(v>>16),(byte)(v>>8),(byte)v};}

    /** the message for this car, or null (logged) when something it needs is missing.
     * theme: "id:version:sdk" (carplay_theme), or empty for none */
    static byte[] build(hmgapi.hook.ModContext mod,android.content.Context context,String theme){
        String pi=null,deviceId=null,sourceVersion=null;
        for(String line:lines(DIPO_INFO)){
            if(line.startsWith("pi="))pi=line.substring(3);
            else if(line.startsWith("deviceID="))deviceId=line.substring(9);
            else if(line.startsWith("sourceVersion="))sourceVersion=line.substring(14);
        }
        if(pi==null || deviceId==null){mod.log("CarPlayStart off: no AirPlay identity from cp-climate yet ("+DIPO_INFO+"); it's saved at the next CarPlay session");return null;}
        String[] wifi=hotspot(context);
        if(wifi==null){mod.log("CarPlayStart off: no hotspot configuration");return null;}
        // LIVI always sends a channel (0 when it doesn't know it); iap2server can't read hostapd.conf
        int channel=channel();
        if(channel<0)channel=apChannel(mod,context);
        if(channel<0)channel=0;
        // at boot wlan0 may have no address yet: armed anyway (the identification is built when the phone
        // links, seconds after boot); the watcher re-arms with the addresses before the phone can ask
        List<String> ips=addresses(HOTSPOT);
        String assetId=null,sdk=null;long version=0;
        String[] t=theme.split(":");
        if(t.length==3 && t[0].length()>0)try{version=Long.parseLong(t[1]);assetId=t[0];sdk=t[2];}catch(NumberFormatException e){assetId=null;}
        byte[] msg=encode(wifi[0],wifi[1],channel,ips,-1,AIRPLAY_PORT,deviceId,pi,sourceVersion,sdk,assetId,version);
        mod.log("CarPlayStart armed: "+msg.length+" B, Wi-Fi set (passphrase "+wifi[1].length()+" chars), channel "+channel+", addresses "+ips
                +", port "+AIRPLAY_PORT+", deviceID "+deviceId+", sourceVersion "+sourceVersion+(assetId!=null?", theme asset "+assetId+" v"+version+" sdk "+sdk:", no theme"));
        return msg;
    }

    /** the hotspot channel from hostapd.conf, or -1 */
    static int channel(){
        int channel=-1;
        for(String line:lines(HOSTAPD))if(line.startsWith("channel="))try{channel=Integer.parseInt(line.substring(8).trim());}catch(NumberFormatException e){channel=-1;}
        return channel>255?-1:channel;
    }
    /** the hotspot channel from the AP configuration's OEM fields (any int field named like *channel*), or -1 */
    static int apChannel(hmgapi.hook.ModContext mod,android.content.Context context){
        try {
            Object wm=context.getSystemService("wifi");
            Object config=wm.getClass().getMethod("getWifiApConfiguration").invoke(wm);
            if(config==null)return -1;
            StringBuilder names=new StringBuilder();int found=-1;
            for(java.lang.reflect.Field f:config.getClass().getFields()){
                if(f.getName().toLowerCase().indexOf("channel")<0)continue;
                names.append(f.getName()).append(' ');
                if(found<0 && f.getType()==int.class){int v=f.getInt(config);if(v>0 && v<=255)found=v;}
            }
            mod.log("CarPlayStart AP channel fields: "+(names.length()>0?names.toString().trim():"none")+" -> "+found);
            return found;
        } catch(Throwable error){return -1;}
    }

    /** {ssid, passphrase} as the stock wireless start reads them (Iap2Service.OnSendWifiInformation) */
    static String[] hotspot(android.content.Context context){
        try {
            Object wm=context.getSystemService("wifi");
            Object config=wm.getClass().getMethod("getWifiApConfiguration").invoke(wm);
            if(config==null)return null;
            String ssid=field(config,"ConnectivitySSID"),psk=field(config,"ConnectivityPSK");
            if(ssid==null)ssid=unquote(field(config,"SSID"));
            if(psk==null)psk=unquote(field(config,"preSharedKey"));
            return ssid!=null && psk!=null?new String[]{ssid,psk}:null;
        } catch(Throwable error){return null;}
    }
    private static String field(Object o,String name){
        try{Object v=o.getClass().getField(name).get(o);return v instanceof String && ((String)v).length()>0?(String)v:null;}
        catch(Throwable e){return null;}
    }
    private static String unquote(String s){return s!=null && s.length()>=2 && s.startsWith("\"") && s.endsWith("\"")?s.substring(1,s.length()-1):s;}

    /** the hotspot's link-local IPv6 (LIVI's choice) first, then its IPv4 */
    static List<String> addresses(String name){
        List<String> out=new ArrayList<String>();
        try {
            NetworkInterface nif=NetworkInterface.getByName(name);if(nif==null)return out;
            String v4=null;
            for(Enumeration<InetAddress> e=nif.getInetAddresses();e.hasMoreElements();){
                InetAddress a=e.nextElement();String s=a.getHostAddress();int pct=s.indexOf('%');if(pct>=0)s=s.substring(0,pct);
                if(a instanceof Inet6Address && a.isLinkLocalAddress())out.add(0,s);
                else if(a instanceof Inet4Address)v4=s;
            }
            // LIVI sends the link-local address only; the IPv4 is kept (after it) only when there is no fe80
            if(v4!=null && out.isEmpty())out.add(v4);
        } catch(Throwable ignored){}
        return out;
    }
    static List<String> lines(String path){
        List<String> out=new ArrayList<String>();
        if(!new File(path).canRead())return out;
        try{BufferedReader r=new BufferedReader(new FileReader(path));try{for(String l;(l=r.readLine())!=null;)out.add(l);}finally{r.close();}}
        catch(Throwable ignored){}
        return out;
    }

    /** logs what the native side reports: the phone's CarPlayAvailability offers and our answers */
    static void watch(final hmgapi.hook.ModContext mod,final android.content.Context context,final String theme,final List<String> initial){
        Thread t=new Thread(new Runnable(){public void run(){
            int offers=0,sent=0;long next=0;List<String> armedWith=initial;
            try {
                while(mod.enabled()){
                    // re-arm whenever the hotspot's addresses change (at boot it has none yet)
                    if(System.currentTimeMillis()>=next){
                        next=System.currentTimeMillis()+3000;
                        List<String> now=addresses(HOTSPOT);
                        if(!now.isEmpty() && !now.equals(armedWith)){
                            byte[] msg=build(mod,context,theme);
                            if(msg!=null){int rc=NativeBridge.startSession(msg);if(rc==0)armedWith=now;else mod.log("CarPlayStart re-arm failed: "+rc);}
                        }
                    }
                    int o=NativeBridge.startSessionStatus(0),s=NativeBridge.startSessionStatus(1);
                    if(o!=offers || s!=sent){
                        int n=Math.min(NativeBridge.startSessionStatus(3),48);
                        StringBuilder hex=new StringBuilder();
                        for(int i=0;i<n;i++){int b=NativeBridge.startSessionStatus(16+i);hex.append(Character.forDigit((b>>4)&15,16)).append(Character.forDigit(b&15,16));}
                        mod.log("CarPlayStart phone CarPlayAvailability #"+o+" ("+NativeBridge.startSessionStatus(3)+" B params "+hex+"), answers sent "+s+", last send "+NativeBridge.startSessionStatus(2));
                        offers=o;sent=s;
                    }
                    Thread.sleep(1000);
                }
            } catch(InterruptedException ended){Thread.currentThread().interrupt();}
        }},"CarPlayStart");
        t.setDaemon(true);t.start();
    }
}
