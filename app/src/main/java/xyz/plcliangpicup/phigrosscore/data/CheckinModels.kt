package xyz.plcliangpicup.phigrosscore.data

import kotlinx.serialization.Serializable

@Serializable data class CheckinChart(val songId: String, val songName: String, val difficulty: String, val targetScore: Int, val targetAccuracy: Double)
@Serializable data class CheckinRecord(val date: String, val coin: Long, val luck: Int, val quoteIndex: Int, val chart: CheckinChart? = null)
@Serializable data class CheckinStatus(val serverDate: String, val month: String, val totalCoin: Long, val totalDays: Long, val today: CheckinRecord? = null, val dates: List<String> = emptyList())
@Serializable data class CheckinRank(val rank: Int, val nickname: String, val totalDays: Long)
@Serializable data class CheckinLeaderboard(val items: List<CheckinRank> = emptyList())

fun checkinLuckText(luck: Int): String = when {
    luck >= 85 -> listOf("长风破浪会有时", "春风得意马蹄疾", "扶摇直上九万里")[luck % 3]
    luck >= 50 -> listOf("行到水穷处，坐看云起时", "一蓑烟雨任平生", "人间有味是清欢")[luck % 3]
    else -> listOf("山重水复，柳暗花明", "莫愁前路无知己", "千磨万击还坚劲")[luck % 3]
}
// Append only: persisted quote indices must continue to identify the same poem.
private val checkinPoems = listOf(
    "「春江潮水连海平，海上明月共潮生」——张若虚《春江花月夜》",
    "「海内存知己，天涯若比邻」——王勃《送杜少府之任蜀州》",
    "「落霞与孤鹜齐飞，秋水共长天一色」——王勃《滕王阁序》",
    "「明月松间照，清泉石上流」——王维《山居秋暝》",
    "「会当凌绝顶，一览众山小」——杜甫《望岳》",
    "「天生我材必有用，千金散尽还复来」——李白《将进酒》",
    "「等闲识得东风面，万紫千红总是春」——朱熹《春日》",
    "「纸上得来终觉浅，绝知此事要躬行」——陆游《冬夜读书示子聿》",
    "「学而不思则罔，思而不学则殆」——《论语·为政》",
    "「知之者不如好之者，好之者不如乐之者」——《论语·雍也》",
    "「三军可夺帅也，匹夫不可夺志也」——《论语·子罕》",
    "「君子喻于义，小人喻于利」——《论语·里仁》",
    "「士不可以不弘毅，任重而道远」——《论语·泰伯》",
    "「见贤思齐焉，见不贤而内自省也」——《论语·里仁》",
    "「千里之行，始于足下」——《老子》第六十四章",
    "「胜人者有力，自胜者强」——《老子》第三十三章",
    "「民为贵，社稷次之，君为轻」——《孟子·尽心下》",
    "「生于忧患而死于安乐也」——《孟子·告子下》",
    "「青，取之于蓝，而青于蓝」——荀子《劝学》",
    "「不积跬步，无以至千里」——荀子《劝学》",
    "「锲而不舍，金石可镂」——荀子《劝学》",
    "「吾尝终日而思矣，不如须臾之所学也」——荀子《劝学》",
    "「师者，所以传道受业解惑也」——韩愈《师说》",
    "「闻道有先后，术业有专攻」——韩愈《师说》",
    "「路曼曼其修远兮，吾将上下而求索」——屈原《离骚》",
    "「亦余心之所善兮，虽九死其犹未悔」——屈原《离骚》",
    "「青青子衿，悠悠我心」——曹操《短歌行》",
    "「山不厌高，海不厌深」——曹操《短歌行》",
    "「羁鸟恋旧林，池鱼思故渊」——陶渊明《归园田居·其一》",
    "「采菊东篱下，悠然见南山」——陶渊明《饮酒·其五》",
    "「悟已往之不谏，知来者之可追」——陶渊明《归去来兮辞》",
    "「安能摧眉折腰事权贵，使我不得开心颜」——李白《梦游天姥吟留别》",
    "「长风破浪会有时，直挂云帆济沧海」——李白《行路难·其一》",
    "「黄沙百战穿金甲，不破楼兰终不还」——王昌龄《从军行·其四》",
    "「无边落木萧萧下，不尽长江滚滚来」——杜甫《登高》",
    "「星垂平野阔，月涌大江流」——杜甫《旅夜书怀》",
    "「安得广厦千万间，大庇天下寒士俱欢颜」——杜甫《茅屋为秋风所破歌》",
    "「同是天涯沦落人，相逢何必曾相识」——白居易《琵琶行》",
    "「别有幽愁暗恨生，此时无声胜有声」——白居易《琵琶行》",
    "「沉舟侧畔千帆过，病树前头万木春」——刘禹锡《酬乐天扬州初逢席上见赠》",
    "「东边日出西边雨，道是无晴却有晴」——刘禹锡《竹枝词二首·其一》",
    "「沧海月明珠有泪，蓝田日暖玉生烟」——李商隐《锦瑟》",
    "「身无彩凤双飞翼，心有灵犀一点通」——李商隐《无题》",
    "「先天下之忧而忧，后天下之乐而乐」——范仲淹《岳阳楼记》",
    "「醉翁之意不在酒，在乎山水之间也」——欧阳修《醉翁亭记》",
    "「不畏浮云遮望眼，自缘身在最高层」——王安石《登飞来峰》",
    "「竹杖芒鞋轻胜马，谁怕？一蓑烟雨任平生」——苏轼《定风波》",
    "「回首向来萧瑟处，归去，也无风雨也无晴」——苏轼《定风波》",
    "「江山如画，一时多少豪杰」——苏轼《念奴娇·赤壁怀古》",
    "「寄蜉蝣于天地，渺沧海之一粟」——苏轼《赤壁赋》",
    "「但愿人长久，千里共婵娟」——苏轼《水调歌头》",
    "「两情若是久长时，又岂在朝朝暮暮」——秦观《鹊桥仙》",
    "「山重水复疑无路，柳暗花明又一村」——陆游《游山西村》",
    "「零落成泥碾作尘，只有香如故」——陆游《卜算子·咏梅》",
    "「人生自古谁无死？留取丹心照汗青」——文天祥《过零丁洋》",
    "「落红不是无情物，化作春泥更护花」——龚自珍《己亥杂诗·其五》",
)

fun checkinPoem(index: Int): String = checkinPoems[Math.floorMod(index, checkinPoems.size)]
