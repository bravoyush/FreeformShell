package com.example.freeformshell

import org.json.JSONArray

object HardwareDatabase {
    private val DB = mutableMapOf<String, HardwareInfo>()

    data class HardwareInfo(
        val modelId: String,
        val marketingName: String,
        val internalCodename: String,
        val gpuModel: String,
        val gpuBaseClockMhz: Int,
        val vulkanVersion: String,
        val openglEsVersion: String,
        val processNode: String
    )

    private val MANUAL_LOOKUP_DATA = """
    [
      {
        "model_id": "SM8850-AB",
        "marketing_name": "Snapdragon 8 Elite Gen 5",
        "internal_codename": "PROJECT_SUNBURST",
        "gpu_model": "Adreno 840",
        "gpu_base_clock_mhz": 1200,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "3nm TSMC N3P"
      },
      {
        "model_id": "SM8850-AC",
        "marketing_name": "Snapdragon 8 Elite Gen 5 for Galaxy",
        "internal_codename": "ORYON_M_STATION",
        "gpu_model": "Adreno 840",
        "gpu_base_clock_mhz": 1250,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "3nm TSMC N3P"
      },
      {
        "model_id": "SM8750-AB",
        "marketing_name": "Snapdragon 8 Elite",
        "internal_codename": "SUN",
        "gpu_model": "Adreno 830",
        "gpu_base_clock_mhz": 1100,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "3nm TSMC N3E"
      },
      {
        "model_id": "SM8750-3-AB",
        "marketing_name": "Snapdragon 8 Elite (Variant)",
        "internal_codename": "PAKALA",
        "gpu_model": "Adreno 830",
        "gpu_base_clock_mhz": 1100,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "3nm TSMC N3E"
      },
      {
        "model_id": "SM8750-AC",
        "marketing_name": "Snapdragon 8 Elite for Galaxy",
        "internal_codename": "SUN_PRO",
        "gpu_model": "Adreno 830",
        "gpu_base_clock_mhz": 1150,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "3nm TSMC N3E"
      },
      {
        "model_id": "SM8735-AB",
        "marketing_name": "Snapdragon 8s Gen 4",
        "internal_codename": "LANAI_LITE",
        "gpu_model": "Adreno 825",
        "gpu_base_clock_mhz": 1100,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4P"
      },
      {
        "model_id": "SM8650-AB",
        "marketing_name": "Snapdragon 8 Gen 3",
        "internal_codename": "PINEAPPLE",
        "gpu_model": "Adreno 750",
        "gpu_base_clock_mhz": 903,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4P"
      },
      {
        "model_id": "SM8650-AC",
        "marketing_name": "Snapdragon 8 Gen 3 for Galaxy",
        "internal_codename": "PINEAPPLE_PRO",
        "gpu_model": "Adreno 750",
        "gpu_base_clock_mhz": 1000,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4P"
      },
      {
        "model_id": "SM8635-AB",
        "marketing_name": "Snapdragon 8s Gen 3",
        "internal_codename": "MICRO",
        "gpu_model": "Adreno 735",
        "gpu_base_clock_mhz": 1100,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4"
      },
      {
        "model_id": "SM8550-AB",
        "marketing_name": "Snapdragon 8 Gen 2",
        "internal_codename": "KALAMA",
        "gpu_model": "Adreno 740",
        "gpu_base_clock_mhz": 680,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4"
      },
      {
        "model_id": "SM8550-AC",
        "marketing_name": "Snapdragon 8 Gen 2 for Galaxy",
        "internal_codename": "KALAMA_PRO",
        "gpu_model": "Adreno 740",
        "gpu_base_clock_mhz": 719,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4"
      },
      {
        "model_id": "SM8475-AB",
        "marketing_name": "Snapdragon 8+ Gen 1",
        "internal_codename": "CAPE",
        "gpu_model": "Adreno 730",
        "gpu_base_clock_mhz": 900,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4"
      },
      {
        "model_id": "SM8450-AB",
        "marketing_name": "Snapdragon 8 Gen 1",
        "internal_codename": "WAIPIO",
        "gpu_model": "Adreno 730",
        "gpu_base_clock_mhz": 818,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm Samsung 4LPX"
      },
      {
        "model_id": "SM8350-AB",
        "marketing_name": "Snapdragon 888 5G",
        "internal_codename": "LAHAINA",
        "gpu_model": "Adreno 660",
        "gpu_base_clock_mhz": 840,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "5nm Samsung 5LPE"
      },
      {
        "model_id": "SM8350-AC",
        "marketing_name": "Snapdragon 888+ 5G",
        "internal_codename": "LAHAINA_H",
        "gpu_model": "Adreno 660",
        "gpu_base_clock_mhz": 840,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "5nm Samsung 5LPE"
      },
      {
        "model_id": "SM8250-AB",
        "marketing_name": "Snapdragon 865 5G",
        "internal_codename": "KONA",
        "gpu_model": "Adreno 650",
        "gpu_base_clock_mhz": 587,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "7nm TSMC N7P"
      },
      {
        "model_id": "SM8250-AC",
        "marketing_name": "Snapdragon 870 5G",
        "internal_codename": "KONA_PRO",
        "gpu_model": "Adreno 650",
        "gpu_base_clock_mhz": 670,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "7nm TSMC N7P"
      },
      {
        "model_id": "SM8150-AB",
        "marketing_name": "Snapdragon 855",
        "internal_codename": "HANA",
        "gpu_model": "Adreno 640",
        "gpu_base_clock_mhz": 585,
        "vulkan_version": "1.1",
        "opengl_es_version": "3.2",
        "process_node": "7nm TSMC N7"
      },
      {
        "model_id": "SM8150-AC",
        "marketing_name": "Snapdragon 855+",
        "internal_codename": "HANA_PRO",
        "gpu_model": "Adreno 640",
        "gpu_base_clock_mhz": 675,
        "vulkan_version": "1.1",
        "opengl_es_version": "3.2",
        "process_node": "7nm TSMC N7"
      },
      {
        "model_id": "SM7675-AB",
        "marketing_name": "Snapdragon 7+ Gen 3",
        "internal_codename": "MICRO_M",
        "gpu_model": "Adreno 732",
        "gpu_base_clock_mhz": 950,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4"
      },
      {
        "model_id": "SM7550-AB",
        "marketing_name": "Snapdragon 7 Gen 3",
        "internal_codename": "CROW",
        "gpu_model": "Adreno 720",
        "gpu_base_clock_mhz": 940,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4"
      },
      {
        "model_id": "SM7475-AB",
        "marketing_name": "Snapdragon 7+ Gen 2",
        "internal_codename": "UKEE",
        "gpu_model": "Adreno 725",
        "gpu_base_clock_mhz": 580,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4"
      },
      {
        "model_id": "SM7450-AB",
        "marketing_name": "Snapdragon 7 Gen 1",
        "internal_codename": "DIABLO",
        "gpu_model": "Adreno 644",
        "gpu_base_clock_mhz": 660,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm Samsung 4LPX"
      },
      {
        "model_id": "SM7325-AA",
        "marketing_name": "Snapdragon 778G 5G",
        "internal_codename": "YUPIK",
        "gpu_model": "Adreno 642L",
        "gpu_base_clock_mhz": 550,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "6nm TSMC N6"
      },
      {
        "model_id": "SM7250-AB",
        "marketing_name": "Snapdragon 765G",
        "internal_codename": "LITO",
        "gpu_model": "Adreno 620",
        "gpu_base_clock_mhz": 625,
        "vulkan_version": "1.1",
        "opengl_es_version": "3.2",
        "process_node": "7nm Samsung 7LPP"
      },
      {
        "model_id": "SM7150-AB",
        "marketing_name": "Snapdragon 730G",
        "internal_codename": "LITO_G",
        "gpu_model": "Adreno 618",
        "gpu_base_clock_mhz": 500,
        "vulkan_version": "1.1",
        "opengl_es_version": "3.2",
        "process_node": "8nm Samsung 8LPP"
      },
      {
        "model_id": "SM6450-AB",
        "marketing_name": "Snapdragon 6 Gen 1",
        "internal_codename": "PARROT",
        "gpu_model": "Adreno 710",
        "gpu_base_clock_mhz": 600,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm Samsung 4LPX"
      },
      {
        "model_id": "SM6375-AB",
        "marketing_name": "Snapdragon 695 5G",
        "internal_codename": "HOLI",
        "gpu_model": "Adreno 619",
        "gpu_base_clock_mhz": 840,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "6nm TSMC N6"
      },
      {
        "model_id": "SM6225-AA",
        "marketing_name": "Snapdragon 680 4G",
        "internal_codename": "BENGAL",
        "gpu_model": "Adreno 610",
        "gpu_base_clock_mhz": 950,
        "vulkan_version": "1.1",
        "opengl_es_version": "3.2",
        "process_node": "6nm TSMC N6"
      },
      {
        "model_id": "SM6125-AA",
        "marketing_name": "Snapdragon 665",
        "internal_codename": "TRIN",
        "gpu_model": "Adreno 610",
        "gpu_base_clock_mhz": 600,
        "vulkan_version": "1.1",
        "opengl_es_version": "3.2",
        "process_node": "11nm Samsung 11LPP"
      },
      {
        "model_id": "MT6993",
        "marketing_name": "MediaTek Dimensity 9500",
        "internal_codename": "DX-5_AETHER",
        "gpu_model": "Immortalis-G935 MC12",
        "gpu_base_clock_mhz": 1800,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "3nm TSMC N3P"
      },
      {
        "model_id": "MT6991",
        "marketing_name": "MediaTek Dimensity 9400",
        "internal_codename": "DX-4_OLYMPUS",
        "gpu_model": "Immortalis-G925 MC12",
        "gpu_base_clock_mhz": 1612,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "3nm TSMC N3E"
      },
      {
        "model_id": "MT6989Z",
        "marketing_name": "MediaTek Dimensity 9300+",
        "internal_codename": "DX-3_PLUS",
        "gpu_model": "Immortalis-G720 MC12",
        "gpu_base_clock_mhz": 1300,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4P"
      },
      {
        "model_id": "MT6989",
        "marketing_name": "MediaTek Dimensity 9300",
        "internal_codename": "DX-3_TITAN",
        "gpu_model": "Immortalis-G720 MC12",
        "gpu_base_clock_mhz": 1300,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4P"
      },
      {
        "model_id": "MT6985Z",
        "marketing_name": "MediaTek Dimensity 9200+",
        "internal_codename": "DX-2_PLUS",
        "gpu_model": "Immortalis-G715 MC11",
        "gpu_base_clock_mhz": 1150,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4P"
      },
      {
        "model_id": "MT6985",
        "marketing_name": "MediaTek Dimensity 9200",
        "internal_codename": "DX-2_GAUGUIN",
        "gpu_model": "Immortalis-G715 MC11",
        "gpu_base_clock_mhz": 981,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4P"
      },
      {
        "model_id": "MT6983Z",
        "marketing_name": "MediaTek Dimensity 9000+",
        "internal_codename": "DX-1_PLUS",
        "gpu_model": "Mali-G710 MC10",
        "gpu_base_clock_mhz": 955,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4"
      },
      {
        "model_id": "MT6983",
        "marketing_name": "MediaTek Dimensity 9000",
        "internal_codename": "DX-1_PICASSO",
        "gpu_model": "Mali-G710 MC10",
        "gpu_base_clock_mhz": 850,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4"
      },
      {
        "model_id": "MT6897Z",
        "marketing_name": "MediaTek Dimensity 8300-Ultra",
        "internal_codename": "DAVINCI_2_ULTRA",
        "gpu_model": "Mali-G615 MC6",
        "gpu_base_clock_mhz": 1400,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4P"
      },
      {
        "model_id": "MT6897",
        "marketing_name": "MediaTek Dimensity 8300",
        "internal_codename": "DAVINCI_2",
        "gpu_model": "Mali-G615 MC6",
        "gpu_base_clock_mhz": 1400,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4P"
      },
      {
        "model_id": "MT6896",
        "marketing_name": "MediaTek Dimensity 8200",
        "internal_codename": "DAVINCI_1",
        "gpu_model": "Mali-G610 MC6",
        "gpu_base_clock_mhz": 950,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm TSMC N4"
      },
      {
        "model_id": "MT6895Z",
        "marketing_name": "MediaTek Dimensity 8100",
        "internal_codename": "DAVINCI_PLUS",
        "gpu_model": "Mali-G610 MC6",
        "gpu_base_clock_mhz": 860,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "5nm TSMC N5"
      },
      {
        "model_id": "MT6895",
        "marketing_name": "MediaTek Dimensity 8000",
        "internal_codename": "DAVINCI",
        "gpu_model": "Mali-G610 MC6",
        "gpu_base_clock_mhz": 700,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "5nm TSMC N5"
      },
      {
        "model_id": "MT6893",
        "marketing_name": "MediaTek Dimensity 1200",
        "internal_codename": "MATISSE",
        "gpu_model": "Mali-G77 MC9",
        "gpu_base_clock_mhz": 866,
        "vulkan_version": "1.2",
        "opengl_es_version": "3.2",
        "process_node": "6nm TSMC N6"
      },
      {
        "model_id": "MT6891",
        "marketing_name": "MediaTek Dimensity 1100",
        "internal_codename": "CHOPIN",
        "gpu_model": "Mali-G77 MC9",
        "gpu_base_clock_mhz": 850,
        "vulkan_version": "1.2",
        "opengl_es_version": "3.2",
        "process_node": "6nm TSMC N6"
      },
      {
        "model_id": "MT6889Z",
        "marketing_name": "MediaTek Dimensity 1000+",
        "internal_codename": "CEZANNE",
        "gpu_model": "Mali-G77 MC9",
        "gpu_base_clock_mhz": 836,
        "vulkan_version": "1.2",
        "opengl_es_version": "3.2",
        "process_node": "7nm TSMC N7"
      },
      {
        "model_id": "MT6889",
        "marketing_name": "MediaTek Dimensity 1000",
        "internal_codename": "MONET",
        "gpu_model": "Mali-G77 MC9",
        "gpu_base_clock_mhz": 836,
        "vulkan_version": "1.2",
        "opengl_es_version": "3.2",
        "process_node": "7nm TSMC N7"
      },
      {
        "model_id": "MT6833",
        "marketing_name": "MediaTek Dimensity 700",
        "internal_codename": "ALISHAN",
        "gpu_model": "Mali-G57 MC2",
        "gpu_base_clock_mhz": 950,
        "vulkan_version": "1.2",
        "opengl_es_version": "3.2",
        "process_node": "7nm TSMC N7"
      },
      {
        "model_id": "S5E9965",
        "marketing_name": "Samsung Exynos 2600",
        "internal_codename": "PROMETHEUS",
        "gpu_model": "Xclipse 960",
        "gpu_base_clock_mhz": 1400,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "2nm Samsung 2GAP"
      },
      {
        "model_id": "S5E9955",
        "marketing_name": "Samsung Exynos 2500",
        "internal_codename": "SOLIS",
        "gpu_model": "Xclipse 950",
        "gpu_base_clock_mhz": 1300,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "3nm Samsung 3GAP"
      },
      {
        "model_id": "S5E9945",
        "marketing_name": "Samsung Exynos 2400",
        "internal_codename": "CALLISTO_ROOT",
        "gpu_model": "Xclipse 940",
        "gpu_base_clock_mhz": 1095,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm Samsung 4LPP+"
      },
      {
        "model_id": "S5E9945E",
        "marketing_name": "Samsung Exynos 2400e",
        "internal_codename": "ROOT_LITE",
        "gpu_model": "Xclipse 940",
        "gpu_base_clock_mhz": 1000,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm Samsung 4LPP+"
      },
      {
        "model_id": "S5E9925",
        "marketing_name": "Samsung Exynos 2200",
        "internal_codename": "PAMIR_VOYAGER",
        "gpu_model": "Xclipse 920",
        "gpu_base_clock_mhz": 1300,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm Samsung 4LPE"
      },
      {
        "model_id": "S5E9840",
        "marketing_name": "Samsung Exynos 2100",
        "internal_codename": "OLYMPUS",
        "gpu_model": "Mali-G78 MP14",
        "gpu_base_clock_mhz": 854,
        "vulkan_version": "1.2",
        "opengl_es_version": "3.2",
        "process_node": "5nm Samsung 5LPE"
      },
      {
        "model_id": "S5E9815",
        "marketing_name": "Samsung Exynos 1080",
        "internal_codename": "ORION",
        "gpu_model": "Mali-G78 MP10",
        "gpu_base_clock_mhz": 840,
        "vulkan_version": "1.2",
        "opengl_es_version": "3.2",
        "process_node": "5nm Samsung 5LPE"
      },
      {
        "model_id": "S5E9830",
        "marketing_name": "Samsung Exynos 990",
        "internal_codename": "PALALLAS_HUBBLE",
        "gpu_model": "Mali-G77 MP11",
        "gpu_base_clock_mhz": 800,
        "vulkan_version": "1.2",
        "opengl_es_version": "3.2",
        "process_node": "7nm Samsung 7LPP"
      },
      {
        "model_id": "GS501",
        "marketing_name": "Google Tensor G5",
        "internal_codename": "LAGUNA",
        "gpu_model": "PowerVR DXT-48-1536",
        "gpu_base_clock_mhz": 1100,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "3nm TSMC N3E"
      },
      {
        "model_id": "GS401",
        "marketing_name": "Google Tensor G4",
        "internal_codename": "ZUMA_PRO",
        "gpu_model": "Immortalis-G715 MP7",
        "gpu_base_clock_mhz": 940,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm Samsung 4LPP+"
      },
      {
        "model_id": "GS301",
        "marketing_name": "Google Tensor G3",
        "internal_codename": "ZUMA",
        "gpu_model": "Immortalis-G715 MP10",
        "gpu_base_clock_mhz": 890,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm Samsung 4LPP+"
      },
      {
        "model_id": "GS201",
        "marketing_name": "Google Tensor G2",
        "internal_codename": "CHEETAH",
        "gpu_model": "Mali-G710 MP7",
        "gpu_base_clock_mhz": 848,
        "vulkan_version": "1.3",
        "opengl_es_version": "3.2",
        "process_node": "4nm Samsung 4LPP"
      },
      {
        "model_id": "GS101",
        "marketing_name": "Google Tensor",
        "internal_codename": "WHITECHAPEL",
        "gpu_model": "Mali-G78 MP20",
        "gpu_base_clock_mhz": 848,
        "vulkan_version": "1.2",
        "opengl_es_version": "3.2",
        "process_node": "5nm Samsung 5LPE"
      }
    ]
    """

    init {
        try {
            val array = JSONArray(MANUAL_LOOKUP_DATA)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val info = HardwareInfo(
                    modelId = obj.getString("model_id"),
                    marketingName = obj.getString("marketing_name"),
                    internalCodename = obj.getString("internal_codename"),
                    gpuModel = obj.getString("gpu_model"),
                    gpuBaseClockMhz = obj.getInt("gpu_base_clock_mhz"),
                    vulkanVersion = obj.getString("vulkan_version"),
                    openglEsVersion = obj.getString("opengl_es_version"),
                    processNode = obj.getString("process_node")
                )
                DB[info.modelId.uppercase()] = info
                DB[info.internalCodename.uppercase()] = info
            }
        } catch (e: Exception) {}
    }

    private val VENDOR_MAP = mapOf(
        "qcom" to "Qualcomm",
        "msm" to "Qualcomm",
        "sdm" to "Qualcomm",
        "sm" to "Qualcomm",
        "mt" to "MediaTek",
        "gs" to "Google",
        "exynos" to "Samsung",
        "s5e" to "Samsung"
    )

    fun getInfo(rawId: String): HardwareInfo? {
        val upper = rawId.uppercase()
        // Try direct match
        DB[upper]?.let { return it }
        // Try partial match (e.g. SM8250-AB matches SM8250)
        DB.keys.forEach { k -> if (upper.contains(k) || k.contains(upper)) return DB[k] }
        return null
    }

    fun getFriendlyCpuName(raw: String): String {
        return getInfo(raw)?.marketingName ?: raw
    }

    fun getFriendlyGpuName(renderer: String): String {
        return renderer.replace("(TM)", "").replace("(R)", "").trim().replace("\\s+".toRegex(), " ")
    }

    fun getProcessNode(soc: String): String {
        return getInfo(soc)?.processNode ?: "Unknown"
    }

    fun getGpuModel(soc: String): String? {
        return getInfo(soc)?.gpuModel
    }

    fun getGpuBaseClock(soc: String): Int {
        return getInfo(soc)?.gpuBaseClockMhz ?: 0
    }

    fun getVendor(raw: String): String {
        val lower = raw.lowercase()
        VENDOR_MAP.forEach { (k, v) -> if (lower.contains(k)) return v }
        return "Generic"
    }
}
