// In-memory seed database for the Xiaohongshu clone mock server.
// Everything is deterministic: rebuilding yields identical data (ids, counts, times
// are anchored to "now" so the feed always looks fresh).
import { hash32, mulberry32 } from './png.mjs';

const HOUR = 3600 * 1000;
const DAY = 24 * HOUR;
const MIN = 60 * 1000;

const pad2 = (n) => String(n).padStart(2, '0');

/** yyyy-MM-ddTHH:mm:ss (local wall clock, no timezone, no millis) — the contract format. */
export function fmt(d) {
  const x = d instanceof Date ? d : new Date(d);
  return (
    `${x.getFullYear()}-${pad2(x.getMonth() + 1)}-${pad2(x.getDate())}` +
    `T${pad2(x.getHours())}:${pad2(x.getMinutes())}:${pad2(x.getSeconds())}`
  );
}

/** yyyy-MM-dd (birthday) */
export function fmtDate(d) {
  const x = d instanceof Date ? d : new Date(d);
  return `${x.getFullYear()}-${pad2(x.getMonth() + 1)}-${pad2(x.getDate())}`;
}

/* ================================================================== *
 * Static seed tables
 * ================================================================== */

const USERS = [
  ['admin', '管理员', 1, '系统管理员，负责这个小红书复刻项目的一切杂事。', '13800138000', '', '北京', '产品经理', '某某大学', '1995-06-01'],
  ['user1', '用户A', 2, '穿搭控 / 咖啡因依赖者 / 周末一定要去山里', '13900001001', '', '上海', '设计师', '某某美术学院', '1998-03-12'],
  ['user2', '用户C', 2, '爱做饭，也爱拍饭。一人食的第 400 天。', '13900001002', '', '广州', '自由职业', '', '2000-09-20'],
  ['user3', '用户E', 1, '爬山涉水，记录山野。工作日写代码，周末当野人。', '13900001003', '', '成都', '程序员', '某某理工大学', '1996-11-05'],
  ['testuser', '测试用户', 0, '这是一个用来测试的账号。', '13900001111', 'test@example.com', '', '', '', ''],
  ['luna', '林小满', 2, '早秋穿搭 / 通勤 ootd / 158 小个子显高', '', '', '杭州', '买手店店员', '某某服装学院', '1999-04-18'],
  ['latte', '拿铁不加糖', 1, '手冲咖啡第三年，拉花还在练。设备党，欢迎交流。', '', '', '深圳', '咖啡师', '', '1994-08-02'],
  ['hike', '山野小鹿', 2, '每周末都在山里。路线可以私信我。', '', '', '重庆', '户外领队', '', '1997-01-25'],
  ['amay', '阿May的彩妆日记', 2, '新手化妆教程 | 平价好物 | 黄皮显白', '', '', '南京', '美妆博主', '', '2001-07-14'],
  ['night', '深夜食堂长', 1, '一人食 / 空气炸锅 / 15 分钟晚餐，主页有群。', '', '', '武汉', '厨师', '', '1993-12-30'],
];

// The five login-able accounts (all passwords are 123456).
const LOGIN_USERS = ['admin', 'user1', 'user2', 'user3', 'testuser'];

// How many notes each user (by array index) has liked / collected — i.e. the new
// amendment-#16 fields `likedPostCount` / `collectedPostCount` (own activity).
// testuser (index 4) has 0 of both on purpose → exercises the "0 → 文字「赞」" branch
// and the empty 「收藏」/「赞过」 lists.
const LIKE_COUNTS = [12, 7, 15, 3, 0, 21, 9, 14, 5, 8];
const COLLECT_COUNTS = [9, 4, 11, 2, 0, 16, 6, 8, 3, 5];

// [followerId, followingId]
// testuser (id 5) deliberately follows NOBODY and has 0 likes/collects, so the
// client's empty states (B5 关注流空态, "0 → 文字「赞」", 0 收藏列表) are reachable.
const FOLLOWS = [
  [1, 2], [1, 6], [1, 7],
  [2, 1], [2, 6],
  [3, 1],
  [4, 1], [4, 2],
  [6, 1], [6, 7],
  [7, 1], [7, 2], [7, 6],
  [8, 1], [8, 2],
  [9, 1],
  [10, 2], [10, 1],
];

/* -------- posts: [title, category] -------- */
const POST_DEFS = [
  ['早秋通勤穿搭｜三件单品搞定办公室「高级感」', '穿搭'],
  ['158 小个子显高穿搭合集，这 9 套照着穿就行', '穿搭'],
  ['温差 10 度的秋天怎么穿？洋葱式叠穿公式', '穿搭'],
  ['黑白灰配色真的不会出错，附色卡参考', '穿搭'],
  ['微胖女生的外套选择指南，这几种版型闭眼入', '穿搭'],
  ['一人食晚餐｜15 分钟搞定的番茄牛腩面', '美食'],
  ['空气炸锅版脆皮鸡翅，零失败配方', '美食'],
  ['周末在家烤一炉可颂，从和面开始记录', '美食'],
  ['广式早茶清单｜本地人带我吃的那几家', '美食'],
  ['新手化妆完整流程｜从妆前到定妆只要 8 步', '彩妆'],
  ['平价粉底液横评｜干皮混干皮看这一篇就够了', '彩妆'],
  ['单眼皮眼妆教程，眼线画法真的不难', '彩妆'],
  ['秋冬口红 8 支试色，黄皮显白推荐', '彩妆'],
  ['今年最好看的 10 部悬疑剧，我刷了三遍', '影视'],
  ['老电影重看｜那些年我们错过的结局', '影视'],
  ['转行做产品经理的第 180 天，真实工作日常', '职场'],
  ['面试被问到「你的缺点是什么」，我这样回答', '职场'],
  ['工作三年才懂的 5 个沟通习惯', '职场'],
  ['异地恋第三年，我们这样维持新鲜感', '情感'],
  ['关于「情绪稳定」，我的一些笨办法', '情感'],
  ['周末徒步路线｜新手友好，全程 8 公里', '徒步'],
  ['徒步装备清单（轻量版），总花费不到 800', '徒步'],
  ['山顶看日出的那一刻，所有疲惫都值得', '徒步'],
  ['手冲咖啡入门｜从磨豆到注水的完整记录', '咖啡'],
  ['在家复刻生椰拿铁，成本不到 5 块', '咖啡'],
  ['咖啡拉花练了 30 天的成果记录', '咖啡'],
  ['城市漫步｜用一天时间走完老城区', '生活'],
  ['收纳整理｜小户型衣柜扩容 300%', '生活'],
  ['养了两年绿植，终于不再手残', '生活'],
  ['我的书桌改造计划，一共花了 300 块', '生活'],
  ['相机新手入门：这 3 个参数一定要先学会', '生活'],
  ['跑步一个月的变化，附每周训练计划', '生活'],
  ['周末去哪玩｜近郊 6 个小众打卡地', '生活'],
  ['一个人看电影的第 100 天', '情感'],
  ['学游泳记录｜从怕水到游完 1000 米', '生活'],
  ['早餐 7 天不重样，简单又好看', '美食'],
  ['租房改造的 5 个小技巧，房东都夸', '生活'],
  ['关于存钱：我把工资分成了 4 份', '职场'],
  ['养猫第一年，我踩过的坑', '生活'],
  ['手账入门｜我的每日记录方式', '生活'],
  ['春天该有的样子，是去公园野餐', '生活'],
  ['学吉他 3 个月，能弹的第一首歌', '生活'],
  ['关于睡眠，我试过的 6 个方法', '生活'],
  ['冬天的第一杯热红酒做法', '美食'],
  ['我的 2026 阅读清单（附短评）', '生活'],
  ['路边摊美食地图｜本地人才知道的 12 家', '美食'],
  ['摄影构图｜9 种让照片变高级的方法', '生活'],
  ['极简生活一年，我扔掉了 200 件东西', '生活'],
  ['周末 vlog｜一个人也要好好吃饭', '美食'],
  ['学英语的 100 天，我用的 3 个方法', '职场'],
];

const CONTENT = {
  穿搭: [
    '最近入秋温差大，我把衣柜里能叠穿的单品全部翻了一遍。\n\n这套的核心是：一件挺括的西装外套 + 一件薄针织 + 一条直筒裤。\n颜色控制在三个以内，整体就会很干净。\n\n• 外套：选择肩线利落的，不要软塌\n• 针织：薄款高领最好用，塞进裤腰显腰线\n• 裤子：九分直筒，露出脚踝更显高\n\n照着穿基本不会出错，适合通勤也适合见朋友。',
    '很多姐妹问我小个子怎么穿显高，其实就三句话：上短下长、同色延伸、露出最细的地方。\n\n我身高 158，平时最常穿的就是高腰直筒裤 + 短款上衣。鞋子选尖头或者厚底，视觉上能多出 5 公分。\n\n这套配色是米白 + 焦糖，秋天穿特别温柔。',
    '秋天最容易穿出层次感的办法就是洋葱式叠穿：内搭薄、中间有型、外面挡风。\n\n早上的 12 度到中午的 22 度，靠一件可以随时脱掉的中间层就能解决。我一般会用衬衫当中间层，既能挡一点风又有型。',
  ],
  美食: [
    '一个人住最怕做饭麻烦，所以我的原则是 15 分钟内必须能吃上。\n\n今天的番茄牛腩面：牛腩提前一晚炖好放冰箱，下班回来只要煮面 + 热汤。番茄要先炒出沙，汤才浓。\n\n• 牛腩：焯水后小火炖 60 分钟\n• 番茄：炒到软烂出汁再加番茄膏\n• 面：另起锅煮，不要直接下在汤里\n\n成本大概 18 块，比外卖香太多了。',
    '空气炸锅真的是租房党的救命神器。这个脆皮鸡翅我做了不下 20 次，配方现在可以闭眼背。\n\n关键是腌够时间 + 表面擦干。200 度 12 分钟，翻面再 8 分钟，皮脆得能听见声音。',
    '周末在家烤可颂，从和面开始记录了一遍。开酥这一步真的需要耐心，黄油一定要够冷，不然就全混进面团里了。\n\n第一次做形状不太完美，但味道已经很接近面包店了。',
  ],
  彩妆: [
    '新手化妆其实不用买一整套，先把这 8 步走完就已经很干净了。\n\n• 妆前：保湿 + 防晒，等 3 分钟再上底妆\n• 底妆：少量多次，用美妆蛋按压\n• 遮瑕：只遮需要的地方\n• 定妆：T 区重点\n\n我第一次画完对比了一下，真的差很多。',
    '干皮选粉底我踩了很多坑，这次把手上 5 支平价粉底都测了一遍，从质地、持妆、氧化三个维度打分。\n\n干皮姐妹重点看「保湿」和「氧化」两列，混干皮可以往中间挑。',
    '单眼皮画眼线一直被说很难，其实关键是不要追求一笔画完。分段画、慢慢连，最后用棉签修一下边缘就很自然了。',
  ],
  影视: [
    '这 10 部是我今年一口气刷完的，没有一部是快进的。按口味从轻到重排了个序，剧荒的时候可以挑一部下饭。\n\n• 前三部：适合周末配外卖\n• 中间四部：需要一点耐心\n• 最后三部：看完会想很久',
    '重看老电影的时候才发现，很多当年没看懂的伏笔其实早就埋好了。这次把结局再看一遍，感觉完全不一样。',
  ],
  职场: [
    '转行做产品第 180 天，记录一下真实的一天：早上对需求，中午写文档，下午开会，晚上改文档。\n\n最大的感受是：表达清楚比想法多重要得多。\n\n如果你也在考虑转行，建议先用业余时间做一个完整的项目再决定。',
    '「你的缺点是什么」这道题其实在考你的自我认知。我的答法是：说一个真实的、正在改进的、不影响核心胜任力的点，然后给出现在采取的具体行动。\n\n千万别答「我太追求完美了」，面试官听了一百遍。',
    '工作三年最想分享的就是沟通习惯：结论先行、给选项而不是给问题、重要的事情一定留痕。\n\n这三条帮我省掉了至少一半的返工。',
  ],
  情感: [
    '异地第三年，我们总结了几个还管用的办法：固定的视频时间、共享的日程表、每个月至少见一次。\n\n距离本身不可怕，可怕的是没有共同话题。所以我们会一起看同一部剧、读同一本书。',
    '所谓情绪稳定，对我来说不是不生气，而是生气的时候知道自己在生气。\n\n我用的笨办法是：先离开现场 10 分钟，把想说的话写下来，冷静了再决定要不要说出口。',
  ],
  徒步: [
    '这条路线对新手很友好，全程 8 公里，爬升 300 米左右，正常速度 3 小时能走完。\n\n• 交通：市区坐公交到终点站，再步行 10 分钟到入口\n• 补给：山上没有商店，水至少带 1.5L\n• 装备：登山鞋 + 登山杖就够了\n\n沿途有大片竹林，夏天也很凉快。',
    '轻量化装备清单，全部加起来不到 800 块。核心思路是：能借的不买，能共用的不重复买。\n\n背包 200 / 登山杖 80 / 冲锋衣 260 / 头灯 45 / 其他 200。',
    '凌晨 4 点出发，6 点 20 分到山顶。太阳出来的那一刻，前面两个小时的黑路突然就都值了。',
  ],
  咖啡: [
    '手冲入门其实只需要四样东西：磨豆机、滤杯、滤纸、秤。\n\n我的参数是 15g 粉，水温 92 度，粉水比 1:15，分三段注水。第一段焖蒸 30 秒，是整杯味道的关键。',
    '在家复刻生椰拿铁，成本不到 5 块。厚椰乳 + 浓缩，冰块一定要加满，比例是 1:1:2。\n\n我用的是速溶冷萃液，味道已经很接近了。',
    '练拉花第 30 天，终于能拉出一个还算完整的心形。\n\n心得是：奶泡的厚度比手法更重要，太厚推不开，太薄就没有形状。',
  ],
  生活: [
    '城市漫步的乐趣在于不看导航。我这次挑了老城区的三条街，走累了就随便找家小店坐下。\n\n一天走了 16000 步，拍了两百多张照片，最喜欢的反而是巷子口那把旧椅子。',
    '小户型收纳的核心是「垂直」和「统一」。我把衣柜里的收纳盒全部换成同一个颜色同一尺寸，视觉上立刻整齐了很多。\n\n再加上分层隔板，容量大概多出了三成。',
    '整理了最近用到的好东西，也顺便记录一下自己的变化。\n\n其实坚持本身没有什么秘诀，就是把门槛降到低得不可能失败。比如想读书就每天只要求看两页，想运动就只要求换上鞋出门。\n\n希望这些记录对你有用。',
  ],
};

const HASHTAGS = {
  穿搭: '#穿搭 #通勤穿搭 #早秋 #ootd',
  美食: '#美食 #一人食 #家常菜 #早餐',
  彩妆: '#彩妆 #新手化妆 #平价好物 #口红试色',
  影视: '#影视推荐 #剧单 #电影',
  职场: '#职场 #打工人 #成长 #经验分享',
  情感: '#情感 #生活记录 #情绪管理',
  徒步: '#徒步 #户外 #周末去哪儿 #爬山',
  咖啡: '#咖啡 #手冲咖啡 #咖啡拉花 #好物分享',
  生活: '#生活记录 #好物分享 #收纳 #citywalk',
};

const COMMENT_POOL = [
  '这套真的太适合通勤了，请问裤子是哪个牌子的？',
  '收藏了，周末就去试试！',
  '这个配色我也有同款，确实好搭。',
  '求链接！！！',
  '写得真好，学到了，谢谢分享。',
  '看完立刻想出门了。',
  '请问小个子可以吗，我 155。',
  '已加入我的收藏夹，慢慢看。',
  '太实用了，正好最近在纠结这个。',
  '照片也拍得太好看了吧，用什么相机？',
  '同城的姐妹吗，可以一起约。',
  '博主更新好快，每次都第一时间看。',
  '这个思路很新，之前完全没想过。',
  '我按你的方法试了一次，真的有用。',
  '能出一期详细的教程吗，蹲一个。',
  '请问预算大概多少，想抄作业。',
  '哈哈哈哈这个描述太真实了。',
  '感谢分享，对我帮助很大。',
  '已经按你说的做完了，效果不错。',
  '看起来好有氛围感，喜欢这种感觉。',
];

const REPLY_POOL = [
  '同款！我也有这个。',
  '链接放在主页啦，可以看看。',
  '谢谢喜欢～',
  '155 也可以的，注意选高腰就行。',
  '我用的就是手机拍的，光线好最重要。',
  '蹲一个后续，记得更新。',
  '楼上说得对，我也是这么想的。',
  '这个问题我也想问。',
  '预算大概三百块左右。',
  '已私信你啦。',
  '哈哈哈哈真的，太懂了。',
  '试过了，确实好用。',
  '可以一起！我周末都有空。',
  '谢谢夸奖，会继续更新的。',
  '这个颜色我也很喜欢。',
  '收藏了，感谢分享。',
  '新手友好吗，怕翻车。',
  '不会翻车的，放心冲。',
  '学到了，明天就试试。',
  '看完就下单了……',
  '我一般会先看价格再决定。',
  '这个方法我试过，效果一般，可能是我手法问题。',
  '可能是水温的问题，试试 92 度。',
  '好详细，感谢博主。',
  '已关注，蹲下期。',
  '秋天穿刚好，不冷不热。',
  '太真实了，我每天都在经历。',
];

const HOT_KEYWORDS = [
  '咖啡拉花', '周末徒步', '早秋穿搭', '新手化妆',
  '职场穿搭', 'citywalk 路线', '家庭烘焙', '显瘦裤子',
  '平价好物', '一个人的旅行',
];

const NOTIFICATION_TYPE_TEXT = {
  1: '赞了你的笔记',
  2: '收藏了你的笔记',
  3: '评论了你的笔记',
  4: '回复了你的评论',
  5: '赞了你的评论',
  6: '关注了你',
};

/** category → allowed notification types (contract §7.2). */
export const CATEGORY_TYPES = {
  1: [1, 2, 5],
  2: [3, 4],
  3: [6],
};

/* ================================================================== *
 * Post metric pools — deliberately spiky so the client hits every
 * display branch (0 → 「赞」, >=10000 → 「1.2万」, hot vs latest ordering).
 * ================================================================== */
const LIKE_POOL = [12000, 0, 356, 15800, 88, 0, 1420, 23, 5600, 0, 912, 47, 3400, 166, 23000, 5, 780, 0, 1200, 62];
const VIEW_POOL = [9800, 320, 1500, 240, 7600, 12000, 400, 62, 3300, 890, 5400, 130, 2100, 40, 8800, 260, 4400, 980, 11000, 300];
const IMG_COUNTS = [1, 1, 3, 2, 1, 4, 2, 1, 3, 1, 1, 5, 2, 9, 1, 3, 1, 2, 4, 1, 2, 1, 6, 1, 3, 2, 1, 1, 2, 3];

// A handful of "quiet" notes that receive NO seeded like/collect record at all.
// They are the only notes that keep a literal `likeCount: 0` / `collectCount: 0`
// after `assertCounterConsistency` below, which is what lets the client render the
// text 「赞」 instead of a number (contract §4) and the 0-list branches.
const QUIET_POST_IDS = new Set([POST_DEFS.length - 3, POST_DEFS.length - 2, POST_DEFS.length - 1, POST_DEFS.length]);

/* ================================================================== *
 * Note-level counter ⇄ record consistency
 *
 * `post.likeCount` / `post.collectCount` are *displayed aggregates* that are
 * seeded to big spiky numbers, independent of the handful of per-user like/collect
 * RECORDS the mock keeps (records exist only so `liked`/`collected` and the
 * 「赞过」/「收藏」 lists have something to page over).
 *
 * That independence is a trap: if a note displays `likeCount: 0` while a like
 * record points at it, then un-liking it cannot decrement below 0, so the
 * decrement is silently lost and a later re-like leaves the counter permanently
 * +1. Over a series of toggles the aggregate drifts.
 *
 * Fix: every note's aggregate must be >= its record count, which makes
 * `counter--` on un-like/un-collect always a real decrement. The reserved
 * QUIET_POST_IDS keep a literal 0 so the client's 「赞」 text branch survives.
 * ================================================================== */
export function assertCounterConsistency(db) {
  const likeRecords = new Map();
  const collectRecords = new Map();
  for (const l of db.postLikes) likeRecords.set(l.postId, (likeRecords.get(l.postId) || 0) + 1);
  for (const c of db.collects) collectRecords.set(c.postId, (collectRecords.get(c.postId) || 0) + 1);
  let bumped = 0;
  for (const p of db.posts) {
    if (QUIET_POST_IDS.has(p.id)) {
      p.likeCount = 0;
      p.collectCount = 0;
      continue;
    }
    const lr = likeRecords.get(p.id) || 0;
    const cr = collectRecords.get(p.id) || 0;
    if (p.likeCount < lr) {
      p.likeCount = lr;
      bumped += 1;
    }
    if (p.collectCount < cr) {
      p.collectCount = cr;
      bumped += 1;
    }
  }
  return {
    bumped,
    quietPosts: [...QUIET_POST_IDS].sort((a, b) => a - b),
    zeroLikePosts: db.posts.filter((p) => p.likeCount === 0).length,
    zeroCollectPosts: db.posts.filter((p) => p.collectCount === 0).length,
  };
}

/* ================================================================== *
 * buildDb
 * ================================================================== */
export function buildDb({ publicBase, now = Date.now() } = {}) {
  const BASE = (publicBase || 'http://localhost:8787').replace(/\/+$/, '');
  const rnd = mulberry32(20260914);

  const db = {
    base: BASE,
    now,
    users: [],
    usersById: new Map(),
    usernameIndex: new Map(),
    posts: [],
    postsById: new Map(),
    comments: [],
    commentsById: new Map(),
    postLikes: [],
    collects: [],
    follows: [],
    notifications: [],
    files: new Map(),
    sessions: new Map(),
    likeSet: new Set(),
    collectSet: new Set(),
    followSet: new Set(),
    commentLikeSet: new Set(),
    counters: { user: 0, post: 0, comment: 0, image: 0, notification: 0, file: 0 },
    imgUrl: (w, h, seed) => `${BASE}/img?w=${w}&h=${h}&seed=${encodeURIComponent(seed)}`,
    avatarUrl: (seed, size = 200) => `${BASE}/avatar?seed=${encodeURIComponent(seed)}&size=${size}`,
    videoUrl: () => `${BASE}/video/sample.mp4`,
  };

  /* ---------------- users ---------------- */
  for (let i = 0; i < USERS.length; i++) {
    const [username, nickname, gender, bio, phone, email, region, occupation, school, birthday] = USERS[i];
    const id = i + 1;
    const createTime = new Date(now - (400 + i * 37) * DAY);
    const u = {
      id,
      username,
      password: '123456',
      nickname,
      avatar: db.avatarUrl(`u${id}-${username}`, 200),
      gender,
      phone: phone || '',
      email: email || '',
      bio,
      backgroundImage: db.imgUrl(800, 450, `bg-u${id}`),
      birthday: birthday || '',
      region: region || '',
      occupation: occupation || '',
      school: school || '',
      redId: String(100000 + id * 137),
      status: 1,
      createTime: fmt(createTime),
      canLogin: LOGIN_USERS.includes(username),
      // derived (filled by recomputeUserStats)
      followingCount: 0,
      followersCount: 0,
      likeCount: 0, // received 获赞数
      collectCount: 0, // received 获藏数
      likeAndCollectCount: 0, // received total
      likedPostCount: 0, // amendment #16 — notes this user liked
      collectedPostCount: 0, // amendment #16 — notes this user collected
    };
    db.users.push(u);
    db.usersById.set(id, u);
    db.usernameIndex.set(username, u);
  }
  db.counters.user = db.users.length;

  /* ---------------- follows ---------------- */
  for (let i = 0; i < FOLLOWS.length; i++) {
    const [followerId, followingId] = FOLLOWS[i];
    const rec = {
      id: i + 1,
      followerId,
      followingId,
      createTime: fmt(new Date(now - (60 + i * 5) * DAY)),
    };
    db.follows.push(rec);
    db.followSet.add(`${followerId}:${followingId}`);
  }

  /* ---------------- posts ---------------- */
  for (let i = 0; i < POST_DEFS.length; i++) {
    const [title, cat] = POST_DEFS[i];
    const id = i + 1;
    // ids 1..6 belong to admin (the logged-in seed user)
    const userId = i < 6 ? 1 : 2 + (i % 9);
    const isVideo = i % 5 === 4;
    const imgCount = IMG_COUNTS[i % IMG_COUNTS.length];
    const [iw, ih] = i % 2 === 0 ? [800, 600] : [600, 800]; // mix 4:3 and 3:4 cards
    const created = now - Math.round((i * 12.7 + rnd() * 6) * HOUR);

    const images = [];
    for (let j = 0; j < imgCount; j++) {
      db.counters.image += 1;
      images.push({
        id: db.counters.image,
        imageUrl: db.imgUrl(iw, ih, `p${id}-${j + 1}`),
        sortOrder: j + 1,
        width: iw,
        height: ih,
      });
    }

    const likeCount = LIKE_POOL[i % LIKE_POOL.length];
    const variants = CONTENT[cat] || CONTENT.生活;
    const body = variants[i % variants.length];
    const content = `${body}\n\n${HASHTAGS[cat] || ''}`;

    const p = {
      id,
      userId,
      title,
      content,
      category: cat,
      type: isVideo ? 1 : 0,
      coverImage: images[0].imageUrl,
      videoUrl: isVideo ? db.videoUrl() : '',
      images,
      viewCount: VIEW_POOL[i % VIEW_POOL.length],
      likeCount,
      commentCount: 0,
      collectCount: Math.floor(likeCount * (0.05 + rnd() * 0.35)),
      status: 1,
      createTime: fmt(new Date(created)),
      updateTime: fmt(new Date(created)),
      _createdMs: created,
    };
    db.posts.push(p);
    db.postsById.set(id, p);
  }
  db.counters.post = db.posts.length;

  /* ---------------- likes / collects ---------------- */
  db.likeSet.clear();
  db.collectSet.clear();
  for (let ui = 0; ui < db.users.length; ui++) {
    const uid = ui + 1;
    const targetLikes = LIKE_COUNTS[ui];
    const targetCollects = COLLECT_COUNTS[ui];
    let placed = 0;
    for (let k = 0; k < db.posts.length && placed < targetLikes; k++) {
      const p = db.posts[(ui * 7 + k * 3) % db.posts.length];
      if (p.userId === uid) continue; // don't like your own note
      if (QUIET_POST_IDS.has(p.id)) continue; // stay at likeCount 0 → 「赞」 branch
      const key = `${uid}:${p.id}`;
      if (db.likeSet.has(key)) continue;
      db.likeSet.add(key);
      db.postLikes.push({
        userId: uid,
        postId: p.id,
        createTime: fmt(new Date(now - (2 + ((ui * 3 + k) % 28)) * DAY - k * 17 * MIN)),
      });
      placed += 1;
    }
    let placedC = 0;
    for (let k = 0; k < db.posts.length && placedC < targetCollects; k++) {
      const p = db.posts[(ui * 11 + k * 5) % db.posts.length];
      if (p.userId === uid) continue;
      if (QUIET_POST_IDS.has(p.id)) continue;
      const key = `${uid}:${p.id}`;
      if (db.collectSet.has(key)) continue;
      db.collectSet.add(key);
      db.collects.push({
        userId: uid,
        postId: p.id,
        createTime: fmt(new Date(now - (1 + ((ui * 5 + k) % 24)) * DAY - k * 23 * MIN)),
      });
      placedC += 1;
    }
  }
  db.eventCounts = assertCounterConsistency(db);

  /* ---------------- comments ---------------- */
  db.comments = [];
  db.commentsById = new Map();
  let cid = 0;
  const nowMs = now;

  function addComment({ postId, userId, content, parentId = 0, replyUserId = 0, atMs, likeCount = 0 }) {
    cid += 1;
    const c = {
      id: cid,
      postId,
      userId,
      content,
      parentId,
      replyUserId,
      likeCount,
      replyCount: 0,
      status: 1,
      createTime: fmt(new Date(atMs)),
      _createdMs: atMs,
    };
    db.comments.push(c);
    db.commentsById.set(cid, c);
    return c;
  }

  // --- the most-commented post: post 1 (admin's) -------------------
  const p1 = db.postsById.get(1);
  const p1Created = p1._createdMs;
  const span = Math.max(1, nowMs - p1Created);

  const big = addComment({
    postId: 1,
    userId: 2,
    content: COMMENT_POOL[0],
    atMs: p1Created + span * 0.08,
    likeCount: 42,
  });
  const mid12 = addComment({
    postId: 1,
    userId: 3,
    content: COMMENT_POOL[1],
    atMs: p1Created + span * 0.2,
    likeCount: 18,
  });
  const small3 = addComment({
    postId: 1,
    userId: 4,
    content: COMMENT_POOL[2],
    atMs: p1Created + span * 0.35,
    likeCount: 6,
  });
  addComment({ postId: 1, userId: 6, content: '求链接！！！', atMs: p1Created + span * 0.55, likeCount: 2 });
  addComment({ postId: 1, userId: 7, content: COMMENT_POOL[4], atMs: p1Created + span * 0.72, likeCount: 0 });
  // a comment by the post author himself → client renders the 「作者」badge
  addComment({ postId: 1, userId: 1, content: '感谢支持，链接整理好会放在评论区置顶～', atMs: p1Created + span * 0.86, likeCount: 11 });

  const replySpecs = [
    [big, 27, 0.09],
    [mid12, 12, 0.22],
    [small3, 3, 0.4],
  ];
  for (const [parent, n, startFrac] of replySpecs) {
    const replySpan = Math.max(1, nowMs - (p1Created + span * startFrac) - 5 * MIN);
    for (let k = 0; k < n; k++) {
      const replyUser = db.users[(k * 3 + parent.id) % db.users.length];
      const text = REPLY_POOL[(k * 5 + parent.id * 3) % REPLY_POOL.length];
      addComment({
        postId: 1,
        userId: replyUser.id,
        content: text,
        parentId: parent.id,
        // every 4th reply answers the note author, the rest answer the top commenter
        replyUserId: k % 4 === 0 ? p1.userId : parent.userId,
        atMs: p1Created + span * startFrac + replySpan * (k / n) + MIN,
        likeCount: k % 6,
      });
      parent.replyCount += 1;
    }
  }

  // --- a few comments on other posts -------------------------------
  for (const p of db.posts) {
    if (p.id === 1) continue;
    const n = p.id <= 20 ? 1 + (p.id % 3) : p.id % 4 === 0 ? 0 : 1;
    const pSpan = Math.max(1, nowMs - p._createdMs);
    const firsts = [];
    for (let k = 0; k < n; k++) {
      const u = db.users[(p.id * 2 + k * 3) % db.users.length];
      const c = addComment({
        postId: p.id,
        userId: u.id,
        content: COMMENT_POOL[(p.id * 3 + k * 7) % COMMENT_POOL.length],
        atMs: p._createdMs + pSpan * (0.3 + 0.55 * ((k + 1) / (n + 1))),
        likeCount: (p.id * 3 + k * 5) % 24,
      });
      firsts.push(c);
    }
    const firstReplyCount = p.id % 3; // 0, 1 or 2 replies on the first comment
    if (firsts.length && firstReplyCount) {
      const parent = firsts[0];
      for (let k = 0; k < firstReplyCount; k++) {
        const u = db.users[(p.id + k * 4) % db.users.length];
        addComment({
          postId: p.id,
          userId: u.id,
          content: REPLY_POOL[(p.id * 2 + k * 6) % REPLY_POOL.length],
          parentId: parent.id,
          replyUserId: parent.userId,
          atMs: parent._createdMs + pSpan * 0.12 * (k + 1),
          likeCount: k % 4,
        });
        parent.replyCount += 1;
      }
    }
  }
  db.counters.comment = cid;

  // commentCount = first-level comments + ALL replies (contract change #13)
  const perPostTotal = new Map();
  for (const c of db.comments) perPostTotal.set(c.postId, (perPostTotal.get(c.postId) || 0) + 1);
  for (const p of db.posts) p.commentCount = perPostTotal.get(p.id) || 0;

  /* ---------------- notifications ---------------- */
  db.notifications = [];
  db.counters.notification = 0;
  function addNotification({ receiverId, senderId, type, postId = 0, commentId = 0, content = '', read = false, ageMin = 60 }) {
    db.counters.notification += 1;
    db.notifications.push({
      id: db.counters.notification,
      receiverId,
      senderId,
      type,
      postId,
      commentId,
      content,
      read,
      createTime: fmt(new Date(now - ageMin * MIN)),
    });
  }

  // admin (id 1): unread across ALL 6 types so all three G-entry badges are non-zero
  const adminPlan = [
    [1, 2, 1, false, 12],
    [1, 6, 3, false, 65],
    [2, 3, 2, false, 130],
    [5, 4, 1, false, 200],
    [3, 7, 4, false, 260],
    [4, 2, 1, false, 310],
    [6, 8, 0, false, 420],
    [3, 9, 5, true, 700],
    [2, 5, 6, true, 900],
    [1, 10, 2, true, 1200],
    [1, 3, 1, true, 1500],
    [4, 6, 2, true, 1900],
    [1, 8, 3, true, 2300],
    [2, 4, 4, true, 2800],
    [5, 9, 5, true, 3300],
    [1, 2, 1, true, 3900],
    [3, 10, 6, true, 4500],
    [1, 7, 2, true, 5200],
    [2, 2, 3, true, 6000],
    [6, 5, 0, true, 7000],
  ];
  // postIds used by admin notifications must be admin's own notes (ids 1..6)
  const adminPostIds = db.posts.filter((p) => p.userId === 1).map((p) => p.id);
  for (let i = 0; i < adminPlan.length; i++) {
    const [type, senderId, postSlot, read, ageMin] = adminPlan[i];
    const postId = type === 6 ? 0 : adminPostIds[postSlot % adminPostIds.length];
    let commentId = 0;
    let content = '';
    if (type === 3 || type === 4) {
      const c = db.comments.find((x) => x.postId === (postId || 1) && x.userId === senderId);
      commentId = c ? c.id : 0;
      content = c ? c.content : COMMENT_POOL[i % COMMENT_POOL.length];
    } else if (type === 5) {
      const c = db.comments.find((x) => x.postId === (postId || 1) && x.userId === 1);
      commentId = c ? c.id : 0;
      content = c ? c.content : '感谢支持，链接整理好会放在评论区置顶～';
    }
    addNotification({ receiverId: 1, senderId, type, postId, commentId, content, read, ageMin });
  }

  // user1 (id 2): must have followUnread === 0 while other categories are > 0
  const user2Plan = [
    [1, 6, 3, false, 20],
    [2, 7, 4, false, 55],
    [1, 10, 2, false, 140],
    [3, 8, 3, true, 800],
    [4, 3, 2, true, 1600],
    [5, 9, 1, true, 2400],
    [6, 1, 0, true, 4000], // read follow → followUnread stays 0
  ];
  const u2PostIds = db.posts.filter((p) => p.userId === 2).map((p) => p.id);
  for (let i = 0; i < user2Plan.length; i++) {
    const [type, senderId, postSlot, read, ageMin] = user2Plan[i];
    const postId = type === 6 ? 0 : u2PostIds[postSlot % u2PostIds.length];
    let commentId = 0;
    let content = '';
    if (type === 3 || type === 4) {
      const c = db.comments.find((x) => x.postId === (postId || 1) && x.userId === senderId);
      commentId = c ? c.id : 0;
      content = c ? c.content : COMMENT_POOL[i % COMMENT_POOL.length];
    }
    addNotification({ receiverId: 2, senderId, type, postId, commentId, content, read, ageMin });
  }

  // a light sprinkle for a couple of other users so the endpoint is never empty
  for (const [receiverId, senderId, ageMin] of [[3, 1, 45], [3, 6, 300], [6, 1, 90], [6, 8, 720], [9, 1, 150]]) {
    const receiverPosts = db.posts.filter((p) => p.userId === receiverId);
    const postId = receiverPosts.length ? receiverPosts[0].id : 0;
    addNotification({ receiverId, senderId, type: 1, postId, read: false, ageMin });
  }

  for (const n of db.notifications) {
    const p = db.postsById.get(n.postId);
    n.postCoverImage = p ? p.coverImage : '';
    n.postTitle = p ? p.title : '';
  }

  recomputeUserStats(db);
  return db;
}

/* ================================================================== *
 * Derived counters (kept consistent with every toggle)
 *
 * Contract amendment #16 — two pairs of deliberately opposite counters:
 *
 *   RECEIVED (获赞数/获藏数, legacy backend semantics):
 *     likeCount           = Σ likeCount of notes authored by this user
 *     collectCount        = Σ collectCount of notes authored by this user
 *     likeAndCollectCount = likeCount + collectCount        (F1 stats row item 3)
 *
 *   OWN ACTIVITY (the new fields, F1 widget-card subtitles):
 *     likedPostCount     = how many notes this user has liked
 *     collectedPostCount = how many notes this user has collected
 *
 * The invariants that must hold for every user:
 *   likedPostCount     === GET /api/like/posts/{id}    .total
 *   collectedPostCount === GET /api/collect/posts/{id} .total
 * which is why likedPostCount/collectedPostCount are counted from the very same
 * records those endpoints page over (db.postLikes / db.collects) rather than kept
 * as independent numbers. It also means any code path that drops a like/collect
 * record (e.g. deleting a note) keeps both sides in step automatically.
 * ================================================================== */
export function recomputeUserStats(db) {
  for (const u of db.users) {
    u.followingCount = 0;
    u.followersCount = 0;
    u.likeCount = 0;
    u.collectCount = 0;
    u.likedPostCount = 0;
    u.collectedPostCount = 0;
  }
  for (const f of db.follows) {
    const a = db.usersById.get(f.followerId);
    const b = db.usersById.get(f.followingId);
    if (a) a.followingCount += 1;
    if (b) b.followersCount += 1;
  }
  // received: sum the counters of the user's own notes
  for (const p of db.posts) {
    const author = db.usersById.get(p.userId);
    if (!author) continue;
    author.likeCount += p.likeCount;
    author.collectCount += p.collectCount;
  }
  // own activity: count the note-level records the client can page over
  for (const l of db.postLikes) {
    const u = db.usersById.get(l.userId);
    if (u) u.likedPostCount += 1;
  }
  for (const c of db.collects) {
    const u = db.usersById.get(c.userId);
    if (u) u.collectedPostCount += 1;
  }
  for (const u of db.users) u.likeAndCollectCount = u.likeCount + u.collectCount;
}

export const SEED_META = {
  notificationTypeText: NOTIFICATION_TYPE_TEXT,
  hotKeywords: HOT_KEYWORDS,
  loginUsers: LOGIN_USERS,
  users: USERS,
  follows: FOLLOWS,
  posts: POST_DEFS,
};

export { NOTIFICATION_TYPE_TEXT, HOT_KEYWORDS, LOGIN_USERS, COMMENT_POOL, REPLY_POOL };
