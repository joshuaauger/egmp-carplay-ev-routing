/* SPDX-License-Identifier: GPL-3.0-or-later
 * CarPlayStartSession (iAP2 0x4301), setting carplay_start_session. Ported from the Ioniq 6 CarPlay
 * Ultra-Lite bundle's EV Routing, but without its own identification builder hook: the Wireless
 * CarPlay mod owns that one (CarPlayIdentification). Here the stock data is patched in place instead.
 *
 * The phone sends CarPlayAvailability (0x4300) once it knows the accessory supports it; the car answers
 * with 0x4301 (Wi-Fi, AirPlay identity, theme asset), which Java encodes. The stock stack knows neither
 * message, so:
 *  - the identification lists them: the length functions of root entries 6 (messages sent) and 7
 *    (messages received) in iAP2IdenInfoMsgList get a wrapper that appends 43 01 / 43 00. Wireless's
 *    builder copies that table for each build, so it picks the wrapper up;
 *  - the 0x4300 handler takes over the dead duplicate 0x5202 entry of iAP2CtlHandleMsgFuncList (the
 *    dispatcher stops at the first 0x5202), and the reply goes out below SendCmd, whose supported-message
 *    check would refuse 0x4301.
 */
#define START_MAX 512
#define AVAILABILITY 0x4300
typedef struct {u32 name,id,label,arg,notify,reply;} Handler;
typedef int (*Blob)(unsigned char *,int,u32,u32,u32);
static unsigned char start_msg[START_MAX];
static u32 start_len,start_offers,start_sent,start_offer_len,start_mutex;
static int start_last=1;
static unsigned char start_offer[64];
static Blob stock_blob[2];   /* entries 6 and 7 */
static void *(*packet_create)(u32,u32);
static void (*packet_destroy)(void *);
static void *(*session_malloc)(u32,const char *,u32);
static int (*send_packet)(void *);
static int (*protect_page)(void *,u32,int);
static Handler *handlers;
static Param *root_params;
static int start_bound;

static u32 start_notify(u32 link,u32 session,const unsigned char *params,u32 n){
    u32 keep=n<sizeof start_offer?n:sizeof start_offer;
    for(u32 i=0;params && i<keep;i++)start_offer[i]=params[i];
    start_offer_len=n;__atomic_add_fetch(&start_offers,1,__ATOMIC_RELEASE);
    u32 len=__atomic_load_n(&start_len,__ATOMIC_ACQUIRE);
    if(!len){start_last=-1;return 0;}
    void *packet=packet_create(link,session);
    if(!packet){start_last=-2;return 0;}
    unsigned char *buf=session_malloc(len,"carplayev",1);
    if(!buf){packet_destroy(packet);start_last=-3;return 0;}
    for(u32 i=0;i<len;i++)buf[i]=start_msg[i];
    ((u32*)packet)[1]=(u32)buf;((u32*)packet)[2]=len;
    start_last=send_packet(packet);
    __atomic_add_fetch(&start_sent,1,__ATOMIC_RELEASE);
    return 0;
}
/* messages sent (6) / received (7) by the accessory: the stock list, then 0x4301 / 0x4300 */
static int start_blob(unsigned char *buf,int cap,u32 id,u32 x,u32 y){
    if(id!=6 && id!=7)return -1;
    int n=stock_blob[id-6](buf,cap,id,x,y);
    if(n<0 || !__atomic_load_n(&start_len,__ATOMIC_ACQUIRE))return n;
    if(!buf)return n+2;
    if(cap-n<2)return n;
    buf[n]=0x43;buf[n+1]=id==6?0x01:0x00;return n+2;
}
static int start_make_writable(void *address,u32 bytes){
    void *page=(void*)((u32)address&~4095U);
    u32 span=((u32)address+bytes-(u32)page+4095U)&~4095U;
    return protect_page(page,span,3)?-19:0;
}
static int start_bind(void){
    if(start_bound)return 0;
    void *lib=dlopen("/system/lib/libiap2session.so",2);
    void *c=dlopen("/system/lib/libc.so",2);
    if(!lib || !c)return -10;
    protect_page=dlsym(c,"mprotect");
    packet_create=dlsym(lib,"iAP2SessionPacketCreate");
    packet_destroy=dlsym(lib,"iAP2SessionPacketDestroy");
    session_malloc=dlsym(lib,"iAP2SessionMalloc");
    send_packet=dlsym(lib,"iAP2ControlSessionSendPacket");
    handlers=dlsym(lib,"iAP2CtlHandleMsgFuncList");
    root_params=dlsym(lib,"iAP2IdenInfoMsgList");
    if(!protect_page || !packet_create || !packet_destroy || !session_malloc || !send_packet || !handlers || !root_params)return -18;
    start_bound=1;return 0;
}
/* The encoded 0x4301 message (40 40 len 43 01 ...), or len 0 for off. Before the phone links. */
static int start_session(const unsigned char *msg,u32 len){
    if(len>START_MAX || (len && (len<6 || msg[0]!=0x40 || msg[1]!=0x40 || ((u32)msg[2]<<8|msg[3])!=len || msg[4]!=0x43 || msg[5]!=0x01)))return -12;
    if(!len){__atomic_store_n(&start_len,0,__ATOMIC_RELEASE);return 0;}
    int rc=start_bind();if(rc)return rc;
    Handler *h=&handlers[33];
    if(handlers[32].id!=0x5202 || (h->id!=0x5202 && h->id!=AVAILABILITY))return -20;
    if(api.lock(&start_mutex))return -15;
    for(u32 i=0;i<len;i++)start_msg[i]=msg[i];
    __atomic_store_n(&start_len,len,__ATOMIC_RELEASE);
    if(h->id!=AVAILABILITY){
        if(start_make_writable(h,sizeof *h)){__atomic_store_n(&start_len,0,__ATOMIC_RELEASE);api.unlock(&start_mutex);return -19;}
        h->notify=(u32)start_notify;h->reply=0;h->arg=0;
        __atomic_store_n(&h->id,AVAILABILITY,__ATOMIC_RELEASE);
    }
    /* the identification's message lists: wrap the stock length functions of entries 6 and 7 */
    int wrapped=0;
    for(u32 i=0;i<24;i++){
        Param *p=&root_params[i];
        if((p->id!=6 && p->id!=7) || !p->length)continue;
        if(p->length==(u32)start_blob){wrapped++;continue;}
        if(start_make_writable(p,sizeof *p)){api.unlock(&start_mutex);return -19;}
        stock_blob[p->id-6]=(Blob)p->length;
        __atomic_store_n(&p->length,(u32)start_blob,__ATOMIC_RELEASE);wrapped++;
    }
    api.unlock(&start_mutex);
    return wrapped==2?0:-22;
}
static int start_status(int which){
    if(which==0)return (int)__atomic_load_n(&start_offers,__ATOMIC_ACQUIRE);
    if(which==1)return (int)__atomic_load_n(&start_sent,__ATOMIC_ACQUIRE);
    if(which==2)return start_last;
    if(which==3)return (int)start_offer_len;
    if(which>=16 && which<16+(int)sizeof start_offer)return start_offer[which-16];
    return -1;
}
