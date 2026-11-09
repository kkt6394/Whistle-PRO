/// 스토어 스크린샷·미리보기 영상용 데모 이름 열다섯 벌(언어마다 한 벌, 워치·폰 공통). 실제 선수·팀과 무관한 가상 이름.
public struct DemoNames: Sendable {
    public let homeName: String, awayName: String
    /// 16명, 1~11 선발, 12·14·15·17·20 후보 (데모 이벤트가 쓰는 1·4·5·7·9·10·14·17 포함)
    public let homeRoster: [Player]
    /// 16명, 1~11 선발, 13·16·18·19·21 후보 (데모 이벤트가 쓰는 4·11 포함)
    public let awayRoster: [Player]
    public let presetName: String
    public let competition: String, venue: String, referee: String

    /// 선발 11명(번호 1~11) + 후보(번호는 benchNumbers). 실제 경기처럼 꽉 찬 명단 — 홍보 영상·스크린샷용
    private static func team(_ starters: [String], bench: [String], benchNumbers: [Int]) -> [Player] {
        zip(1...11, starters).map { Player(number: $0.0, name: $0.1, starter: true) }
            + zip(benchNumbers, bench).map { Player(number: $0.0, name: $0.1) }
    }
    private static func home(_ starters: [String], bench: [String]) -> [Player] {
        team(starters, bench: bench, benchNumbers: [12, 14, 15, 17, 20])
    }
    private static func away(_ starters: [String], bench: [String]) -> [Player] {
        team(starters, bench: bench, benchNumbers: [13, 16, 18, 19, 21])
    }

    public static let ko = DemoNames(
        homeName: "FC 서울숲", awayName: "한강 유나이티드",
        homeRoster: home(["김민준", "서지훈", "임건우", "이서준", "박도윤", "오현우", "최하준", "신유준", "정시우", "한예준", "권태윤"],
                         bench: ["황민재", "조은우", "안준서", "윤도현", "송재원"]),
        awayRoster: away(["배성민", "류지환", "홍승현", "정우진", "문태호", "양동현", "구본혁", "남찬영", "노준혁", "하승우", "강지호"],
                         bench: ["차민성", "장도윤", "엄태준", "전호진", "고성민"]),
        presetName: "조기축구 25분×4",
        competition: "서울 아마추어 리그", venue: "서울숲 운동장", referee: "김민수")

    public static let en = DemoNames(
        homeName: "Riverside FC", awayName: "Harbor United",
        homeRoster: home(["Carter", "Walsh", "Mensah", "Nguyen", "Okafor", "Hughes", "Silva", "Reyes", "Park", "Müller", "Diallo"],
                         bench: ["Brennan", "Johansson", "Kowalski", "Dubois", "Ortiz"]),
        awayRoster: away(["Fischer", "Lindqvist", "Moreau", "Rossi", "Adeyemi", "Costa", "O'Brien", "Haddad", "Novak", "Petrov", "Tanaka"],
                         bench: ["Larsen", "Bianchi", "Murphy", "Santos", "Weber"]),
        presetName: "Sunday League 25×4",
        competition: "Riverside Amateur League", venue: "Riverside Park", referee: "A. Kim")

    public static let ja = DemoNames(
        homeName: "FC 多摩川", awayName: "隅田ユナイテッド",
        homeRoster: home(["佐藤", "鈴木", "高橋", "田中", "伊藤", "渡辺", "山本", "中村", "小林", "加藤", "吉田"],
                         bench: ["山田", "佐々木", "松本", "井上", "木村"]),
        awayRoster: away(["林", "清水", "山崎", "森", "池田", "橋本", "阿部", "石川", "前田", "藤田", "小川"],
                         bench: ["岡田", "後藤", "長谷川", "村上", "近藤"]),
        presetName: "草サッカー 25分×4",
        competition: "東京アマチュアリーグ", venue: "多摩川河川敷グラウンド", referee: "中島 健")

    public static let zhHans = DemoNames(
        homeName: "滨江FC", awayName: "西湖联队",
        homeRoster: home(["王磊", "李伟", "张强", "刘洋", "陈杰", "杨帆", "赵鹏", "黄涛", "周斌", "吴昊", "徐峰"],
                         bench: ["孙超", "马骏", "朱亮", "胡鑫", "郭凯"]),
        awayRoster: away(["何冰", "高翔", "林浩", "罗兵", "郑宇", "梁晨", "谢飞", "宋健", "唐龙", "韩旭", "冯博"],
                         bench: ["邓勇", "曹阳", "彭刚", "曾辉", "肖军"]),
        presetName: "业余联赛 25分×4",
        competition: "杭州业余联赛", venue: "滨江体育公园", referee: "陈明")

    public static let th = DemoNames(
        homeName: "เจ้าพระยา เอฟซี", awayName: "ลุมพินี ยูไนเต็ด",
        homeRoster: home(["สมชาย", "ธนพล", "วีรพงษ์", "อนุชา", "กิตติ", "ณัฐวุฒิ", "ปิยะ", "สุรเชษฐ์", "ชัยวัฒน์", "ภาณุ", "ธีรวัฒน์"],
                         bench: ["อภิชาติ", "วรวุฒิ", "ศักดิ์ชัย", "พงศกร", "นพดล"]),
        awayRoster: away(["ประเสริฐ", "เอกชัย", "สุทธิพงษ์", "จักรพันธ์", "วิทยา", "อดิศักดิ์", "ธวัชชัย", "สมศักดิ์", "กฤษดา", "ชนาธิป", "รัฐพล"],
                         bench: ["บุญมี", "ไพโรจน์", "สราวุธ", "ปกรณ์", "ทรงพล"]),
        presetName: "ฟุตบอลเช้า 25 นาที×4",
        competition: "ลีกสมัครเล่นกรุงเทพฯ", venue: "สนามริมเจ้าพระยา", referee: "สมศักดิ์ ใจดี")

    public static let vi = DemoNames(
        homeName: "FC Sông Hồng", awayName: "Hồ Tây United",
        homeRoster: home(["Minh", "Hùng", "Tuấn", "Dũng", "Nam", "Quang", "Hải", "Long", "Phúc", "Khánh", "Thành"],
                         bench: ["Sơn", "Trung", "Huy", "Vinh", "Đức"]),
        awayRoster: away(["Bình", "Cường", "Hiếu", "Toàn", "Thắng", "Lâm", "Tài", "Hưng", "Kiên", "Quân", "Tú"],
                         bench: ["Lộc", "Hòa", "Nghĩa", "Hậu", "Thịnh"]),
        presetName: "Bóng đá phong trào 25'×4",
        competition: "Giải nghiệp dư Hà Nội", venue: "Sân bãi sông Hồng", referee: "Trần Minh")

    public static let id = DemoNames(
        homeName: "Ciliwung FC", awayName: "Menteng United",
        homeRoster: home(["Budi", "Agus", "Rizky", "Dimas", "Fajar", "Bayu", "Andi", "Eko", "Hendra", "Yoga", "Arif"],
                         bench: ["Ilham", "Reza", "Teguh", "Wahyu", "Gilang"]),
        awayRoster: away(["Dedi", "Joko", "Rudi", "Bambang", "Aditya", "Fikri", "Hadi", "Irfan", "Rangga", "Sigit", "Taufik"],
                         bench: ["Yusuf", "Bagas", "Dwi", "Galih", "Rafi"]),
        presetName: "Bola Minggu 25'×4",
        competition: "Liga Amatir Jakarta", venue: "Lapangan Ciliwung", referee: "Andi Pratama")

    public static let ptBR = DemoNames(
        homeName: "Ibirapuera FC", awayName: "Tietê United",
        homeRoster: home(["Silva", "Santos", "Oliveira", "Souza", "Lima", "Pereira", "Costa", "Rodrigues", "Almeida", "Nascimento", "Carvalho"],
                         bench: ["Gomes", "Ribeiro", "Martins", "Rocha", "Barbosa"]),
        awayRoster: away(["Ferreira", "Araújo", "Melo", "Cardoso", "Teixeira", "Moreira", "Correia", "Dias", "Pinto", "Freitas", "Vieira"],
                         bench: ["Mendes", "Castro", "Monteiro", "Nunes", "Campos"]),
        presetName: "Pelada 25'×4",
        competition: "Liga Amadora Paulista", venue: "Campo do Ibirapuera", referee: "R. Andrade")

    public static let es = DemoNames(
        homeName: "Chapultepec FC", awayName: "Coyoacán FC",
        homeRoster: home(["García", "Hernández", "López", "Martínez", "González", "Pérez", "Rodríguez", "Sánchez", "Ramírez", "Torres", "Flores"],
                         bench: ["Rivera", "Gómez", "Díaz", "Cruz", "Morales"]),
        awayRoster: away(["Reyes", "Ortiz", "Gutiérrez", "Chávez", "Ruiz", "Mendoza", "Aguilar", "Vargas", "Castillo", "Jiménez", "Romero"],
                         bench: ["Herrera", "Medina", "Silva", "Ramos", "Navarro"]),
        presetName: "Liga dominical 25'×4",
        competition: "Liga Amateur del Valle", venue: "Campo Chapultepec", referee: "J. Morales")

    public static let de = DemoNames(
        homeName: "FC Isarauen", awayName: "SV Eisbach",
        homeRoster: home(["Müller", "Schmidt", "Schneider", "Fischer", "Weber", "Meyer", "Wagner", "Becker", "Schulz", "Hoffmann", "Koch"],
                         bench: ["Richter", "Klein", "Wolf", "Neumann", "Schwarz"]),
        awayRoster: away(["Zimmermann", "Braun", "Krüger", "Hartmann", "Lange", "Werner", "Krause", "Lehmann", "Köhler", "Maier", "Huber"],
                         bench: ["Kaiser", "Fuchs", "Peters", "Scholz", "Möller"]),
        presetName: "Freizeitliga 25 Min.×4",
        competition: "Kreisliga München", venue: "Sportplatz Isarauen", referee: "T. Bauer")

    public static let fr = DemoNames(
        homeName: "FC Seine", awayName: "AS Montsouris",
        homeRoster: home(["Martin", "Bernard", "Dubois", "Thomas", "Robert", "Richard", "Petit", "Durand", "Leroy", "Moreau", "Simon"],
                         bench: ["Laurent", "Lefèvre", "Michel", "Garcia", "David"]),
        awayRoster: away(["Bertrand", "Roux", "Vincent", "Fournier", "Morel", "Girard", "André", "Mercier", "Dupont", "Lambert", "Bonnet"],
                         bench: ["François", "Martinez", "Legrand", "Garnier", "Faure"]),
        presetName: "Foot loisir 25 min×4",
        competition: "Ligue amateur de Paris", venue: "Stade des bords de Seine", referee: "L. Rousseau")

    public static let it = DemoNames(
        homeName: "FC Navigli", awayName: "AS Sempione",
        homeRoster: home(["Rossi", "Russo", "Ferrari", "Esposito", "Bianchi", "Romano", "Colombo", "Ricci", "Marino", "Greco", "Bruno"],
                         bench: ["Gallo", "Conti", "De Luca", "Mancini", "Costa"]),
        awayRoster: away(["Giordano", "Rizzo", "Lombardi", "Moretti", "Barbieri", "Fontana", "Santoro", "Mariani", "Rinaldi", "Caruso", "Ferrara"],
                         bench: ["Galli", "Martini", "Leone", "Longo", "Gentile"]),
        presetName: "Calcio amatori 25'×4",
        competition: "Campionato amatori Milano", venue: "Campo dei Navigli", referee: "M. Fabbri")

    public static let nl = DemoNames(
        homeName: "FC Amstelpark", awayName: "Vondel United",
        homeRoster: home(["De Jong", "Jansen", "De Vries", "Van den Berg", "Bakker", "Visser", "Smit", "Meijer", "De Boer", "Mulder", "Bos"],
                         bench: ["Vos", "Peters", "Hendriks", "Dekker", "Brouwer"]),
        awayRoster: away(["Van Dijk", "Janssen", "Van Leeuwen", "De Groot", "Kok", "Willems", "Jacobs", "Vermeulen", "Van der Linden", "Schouten", "Hoekstra"],
                         bench: ["Prins", "Koster", "Verhoeven", "Post", "Kuiper"]),
        presetName: "Zondagvoetbal 25 min×4",
        competition: "Amateurcompetitie Amsterdam", venue: "Sportpark Amstelpark", referee: "R. de Wit")

    public static let pl = DemoNames(
        homeName: "KS Bulwary", awayName: "Błonia United",
        homeRoster: home(["Nowak", "Kowalski", "Wiśniewski", "Wójcik", "Kowalczyk", "Kamiński", "Lewandowski", "Zieliński", "Szymański", "Woźniak", "Dąbrowski"],
                         bench: ["Kozłowski", "Jankowski", "Mazur", "Kwiatkowski", "Krawczyk"]),
        awayRoster: away(["Piotrowski", "Grabowski", "Nowakowski", "Pawłowski", "Michalski", "Król", "Wieczorek", "Jabłoński", "Wróbel", "Majewski", "Olszewski"],
                         bench: ["Stępień", "Malinowski", "Jaworski", "Adamczyk", "Dudek"]),
        presetName: "Liga osiedlowa 25 min×4",
        competition: "Liga amatorska Kraków", venue: "Boisko Bulwary Wiślane", referee: "P. Zając")

    public static let tr = DemoNames(
        homeName: "Moda FC", awayName: "Boğaziçi United",
        homeRoster: home(["Yılmaz", "Kaya", "Demir", "Şahin", "Çelik", "Yıldız", "Yıldırım", "Öztürk", "Aydın", "Özdemir", "Arslan"],
                         bench: ["Doğan", "Kılıç", "Aslan", "Çetin", "Kara"]),
        awayRoster: away(["Koç", "Kurt", "Özkan", "Şimşek", "Polat", "Korkmaz", "Erdoğan", "Güneş", "Aktaş", "Bulut", "Keskin"],
                         bench: ["Tekin", "Ünal", "Güler", "Acar", "Turan"]),
        presetName: "Halı saha ligi 25 dk×4",
        competition: "İstanbul Amatör Ligi", venue: "Moda Sahası", referee: "E. Tunç")

    /// 실행 인자 값(언어 코드) → 이름 벌
    public static let byLanguage: [String: DemoNames] = [
        "ko": .ko, "en": .en, "ja": .ja, "zh-Hans": .zhHans, "th": .th, "vi": .vi, "id": .id, "pt-BR": .ptBR, "es": .es, "de": .de, "fr": .fr, "it": .it, "nl": .nl, "pl": .pl, "tr": .tr,
    ]

    /// "-demoNames" 다음 값(언어 코드: ko·en·ja·zh-Hans·…)의 이름 벌, 인자가 없거나 모르는 값이면 nil (스토어 모드 아님)
    public static func fromArguments(_ arguments: [String]) -> DemoNames? {
        guard let index = arguments.firstIndex(of: "-demoNames"),
              index + 1 < arguments.count else { return nil }
        return byLanguage[arguments[index + 1]]
    }
}
