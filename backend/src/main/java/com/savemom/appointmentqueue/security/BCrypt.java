package com.savemom.appointmentqueue.security;

import java.security.SecureRandom;

/**
 * BCrypt password hashing algorithm implementation in pure Java.
 * Originally developed by Damien Miller (OpenBSD).
 * Provides salted, adaptive one-way password hashing using the Blowfish cipher.
 */
public class BCrypt {

    private static final int BCRYPT_SALT_LEN = 16;
    private static final int BLOWFISH_NUM_ROUNDS = 16;

    // Blowfish P-box and S-boxes initialization constants
    private static final int[] P_ORIG = {
            0x243f6a88, 0x85a308d3, 0x13198a2e, 0x03707344, 0xa4093822,
            0x299f31d0, 0x082efa98, 0xec4e6c89, 0x452821e6, 0x38d01377,
            0xbe5466cf, 0x34e90c6c, 0xc0ac29b7, 0xc97c50dd, 0x3f84d5b5,
            0xb5470917, 0x9216d5d9, 0x8979fb1b
    };

    private static final int[] S_ORIG_0 = {
            0xd1310ba6, 0x98dfb5ac, 0x2ffd72db, 0xd01adfb7, 0xb8e1afed,
            0x6a267e96, 0xba7c9045, 0xf12c7f99, 0x24a19947, 0xb3916cf7,
            0x0801f2e2, 0x858efc16, 0x636920d8, 0x71574e69, 0xa458fea3,
            0xf4933d7e, 0x0d95748f, 0x728eb658, 0x718bcd58, 0x82154aee,
            0x7b54a41d, 0xc25a59b5, 0x9c30d539, 0x2af26013, 0xc5d1b023,
            0x286085f0, 0xca417918, 0xb8db38ef, 0x8e79dcb0, 0x603a180e,
            0x6c9e0e8b, 0xb01e8a3e, 0xd71577c1, 0xbd314b27, 0x78af2fda,
            0x55605c60, 0xe65525f3, 0xaa55ab94, 0x57489862, 0x63e81440,
            0x55ca396a, 0x2aab10b6, 0xb4cc5c34, 0x1141e8ce, 0xa15486af,
            0x7c72e993, 0xb3ee1411, 0x636fbc2a, 0x2ba9c55d, 0x741831f6,
            0xce5c3e16, 0x9b87931e, 0xafd6ba33, 0x6c24cf5c, 0x7a325381,
            0x28958677, 0x3b8f4898, 0x6b4bb9af, 0xc4bfe81b, 0x66282193,
            0x61d809cc, 0xfb21a991, 0x487cac60, 0x5dec8032, 0xef845d5d,
            0xe98575b1, 0xdc262302, 0xeb651b88, 0x23893e81, 0xd396acc5,
            0x0f6d6ff3, 0x83f44239, 0x2e0b4482, 0xa4842004, 0x69c8f04a,
            0x9e1f9b5e, 0x21c66842, 0xf6e96c9a, 0x670c9c61, 0xabd388f0,
            0x6a51a0d2, 0xd8542f68, 0x960fa728, 0xab5133a3, 0x6eef0b6c,
            0x137a3be4, 0xba3bf050, 0x7efb2bbe, 0x8b347667, 0x1e6e2406,
            0xf1cc33e3, 0x66bf1407, 0x4170b0e0, 0xfe2d4a9d, 0x6cf21a6e,
            0x7c4904fb, 0x07f4ac9f, 0x47d061ff, 0x98fe64b0, 0xac654483,
            0xb249339e, 0xaf763a6c, 0x442ff9c1, 0x63579976, 0x73a2468f,
            0xd0c448bb, 0xb6552943, 0xb1b0451a, 0x1d36d4df, 0x1b40283f,
            0xd20b78c4, 0x3d002f54, 0x62955f9a, 0x9953282b, 0xc27a4a98,
            0x72922129, 0x3f5b7ea5, 0x74299b64, 0xb69736c4, 0x64425bf7,
            0xd3e4215f, 0x77d130a7, 0x1404c0cf, 0x052e463a, 0x19277d33,
            0x180bb292, 0x29bcf82e, 0x4cb19c62, 0x97dd60f9, 0x2288001e,
            0xb62ef2a9, 0x72e0d37e, 0x6c6c7d1e, 0x69246816, 0x8543445a,
            0xc4d80922, 0x973a216e, 0xcb9921b0, 0xa1177651, 0xa0849767,
            0x01532104, 0xd0ca5061, 0x1338df6c, 0x10d199f7, 0xb9708ca6,
            0x61661334, 0xcd693521, 0x69b5bb82, 0x8ecb2f4c, 0x977cb089,
            0x600b396b, 0x84d33939, 0x4c0a5202, 0x2bbef447, 0x5bb8abeb,
            0x2943ff67, 0x3e404080, 0x47e70404, 0xb0e82c81, 0x2b2eb005,
            0x58bf2a0b, 0x27fcff79, 0x2a9e3388, 0x192a6c8b, 0xcb806124,
            0x5d9b62c1, 0xeb15949d, 0x60b29864, 0xb816a7f8, 0xbbf74e89,
            0xd8f5f190, 0x2424b914, 0x6011124f, 0x8bfa0c8b, 0xa436c84c,
            0xa49766ee, 0x87a55547, 0x9188d3e2, 0x1a879893, 0xd7b40d02,
            0xa013b96f, 0xa39d2242, 0x9b2a40b9, 0xda098a0f, 0xa31e422f,
            0x660f5319, 0xd6c40608, 0x1e360b8a, 0x95c73a0c, 0xb05504d6,
            0xbbca051a, 0xb8880a1e, 0x80b6fc7d, 0xbf078c8a, 0x0ebf30ec,
            0xeb7a0a6d, 0x76b4d307, 0x34d58850, 0xa1eb0179, 0xe004121a,
            0xe33390c2, 0xfae11368, 0x6ba04d9e, 0x0e5038ee, 0x249a3c20,
            0x8898f828, 0x651fcb5c, 0x5776d654, 0xfaac5a1a, 0x6e257eb8,
            0x8ebdd8d4, 0x7a285d51, 0xa4a2b2e5, 0x61a8ef14, 0x673e4492,
            0x4b7b204e, 0x80905188, 0x28975871, 0xb1b56930, 0x794625b0,
            0x88f583eb, 0x26c79a60, 0xd06b7294, 0xa9b77dd1, 0x49f50e80,
            0x1d667730, 0x850acda4, 0x7b779496, 0x610a2b02, 0x9c4883bb,
            0x6e41416a, 0xa3447551, 0x2287be45, 0x80f2d4e0, 0x6543bda6,
            0x0286822a, 0x95973e6a, 0x8d5b086e, 0x0a149b5e, 0x96f60045,
            0x7466e344, 0x2ec680b1, 0x7706f97d, 0x3d70b0e0, 0x550a2212,
            0x6b1a774c, 0xa8f000b0, 0x81be7f2a, 0x6600c732, 0x67379b0f,
            0x8825b16a, 0x3ca6a610, 0x97d02fe1, 0x4be102f3, 0x30606e12, 0x10763899
    };

    private static final int[] S_ORIG_1 = {
            0x4b7a70e9, 0xb5b32944, 0xdb75092e, 0xc4192623, 0xad6ea6b0,
            0x49a7df7d, 0x9cee60b8, 0x8fedb266, 0xecaa8c71, 0x699a17ff,
            0x5664526c, 0xc3d19974, 0xba9543e4, 0x82f480ec, 0x99aee295,
            0x0139be70, 0xd6bf839f, 0x553403d2, 0xf15c62d1, 0x415b7632,
            0xa0c2e113, 0x61447092, 0xc32220dd, 0x854060b1, 0xa4e725e4,
            0x26e47cd4, 0xf4ef06b5, 0xa0e12b10, 0x6ae26f04, 0x8063e520,
            0x45f269a8, 0x414000ee, 0x4e0a3094, 0x34f709e4, 0xd5426b66,
            0x6511a226, 0xae6ba366, 0xcbe113b5, 0x7173bc3d, 0x16347061,
            0x9ec739ee, 0x3560f7f1, 0x61a6b704, 0x754398e8, 0x7ca07d98,
            0x653ec930, 0x499b9a4f, 0x85ec3f8d, 0xfb227a19, 0x40e46170,
            0x47b1c521, 0x9737995b, 0xca6ec22c, 0x3011942f, 0x40e42e14,
            0x8b32932a, 0x93700010, 0x49e75d41, 0xe97973f6, 0x4b37d825,
            0x66c66066, 0xbf374970, 0x64d6149d, 0xbf4d5597, 0xe139f92f,
            0x634f1640, 0x4604d531, 0xdf934f37, 0x40950552, 0x2285b4b0,
            0x1b46327b, 0x1cf92a1d, 0x0608dd03, 0x1f77242f, 0xf0276c4e,
            0xa30e7054, 0x673770f7, 0x139e2fe0, 0x62cf4d1a, 0xa4d41b76,
            0x429b023d, 0x37d0d724, 0xd00a1248, 0xdb0fead3, 0x49f1c09b,
            0x075372c9, 0x80991b7b, 0x25d479d8, 0xf6e8def7, 0xe3fe501a,
            0xb6794c3b, 0x976ce0bd, 0x04c006ba, 0xc1a94fb6, 0x409f60c4,
            0x5e5c9ec2, 0x196a2463, 0x68fb6faf, 0x3e6c53b5, 0x1339b2eb,
            0x3b52ec6f, 0x6dfc511f, 0x9b30952c, 0xcc814544, 0xaf5ebd09,
            0xbee3d004, 0xde334afd, 0x660f2807, 0x192e4bb3, 0xc0cba857,
            0x45c8740f, 0xd20b5f39, 0xb9d3fbdb, 0x5579c0bd, 0x1a60320a,
            0xd6a100c6, 0x402c7279, 0x679f25fe, 0xfb1fa3cc, 0x8ea5e9f8,
            0xdb3222f8, 0x3c7516df, 0xfd616b15, 0x2f501ec8, 0xad0552ab,
            0x323db5fa, 0xfd238760, 0x53317b48, 0x3e00df82, 0x9e5c57bb,
            0xca6f8ca0, 0x1a87562e, 0xdf1769db, 0xd542a8f6, 0x287effc3,
            0xac6732c6, 0x8c4f5573, 0x695b27b0, 0xbbca58c8, 0xe1ffa35d,
            0xb8f011a0, 0x10fa3d98, 0xfd2183b8, 0x4afcb56c, 0x2dd1d35b,
            0x9a53e479, 0xb6f84565, 0xd28e49bc, 0x4bfb9790, 0xe1ddf2da,
            0xa4da7eb7, 0x61649304, 0x4647fe3b, 0x7185e79d, 0xfa5f6947,
            0xa043f55e, 0x47b295c9, 0xa0c0e5d5, 0xbecfbf85, 0x95386724,
            0x614cc970, 0x801a2235, 0x42f741aa, 0xab91295f, 0x92b62c41,
            0x40a02cb4, 0x6ec9a01f, 0x503487fa, 0x44933820, 0x565de3b0,
            0x41e80a12, 0x071fef45, 0x46e01860, 0x73e861d7, 0x84d670a8,
            0x5194c93f, 0xa64d95a2, 0x4d422f08, 0x45f381f5, 0x4dd46648,
            0xd135480e, 0x747fc397, 0x61ac10b9, 0x75d5a7e2, 0x82f27e1d,
            0x0618bc08, 0x990f0967, 0xb14a604e, 0x70747514, 0x034dd4ec,
            0x86e660f3, 0x874e5517, 0x56e3db65, 0xbbe123ac, 0xa86f3374,
            0xe0bd7b36, 0x5138a1dc, 0xcfc8324f, 0x67a49ffb, 0x26114047,
            0x86134f80, 0xb738f520, 0x5eb0fb5e, 0x642f4c9f, 0x02feabb7,
            0x0ff5c6b0, 0x58b6983f, 0x46654c2d, 0xa4dd8462, 0xca744211,
            0x9824f4f3, 0xdbf82fdd, 0x806ecce1, 0x4c142e13, 0x6c6d9343,
            0x37a4ce9c, 0xd3217abf, 0x43665077, 0x438f92e0, 0xa6339942,
            0x43907221, 0x0c921943, 0x23c467e3, 0x7e00d07c, 0xeb2957b2,
            0xee3952c5, 0x535ec70f, 0x1da517ab, 0x315f5684, 0x426da7a2,
            0x4da14238, 0x301988e9, 0xa2b17d5c, 0x6a445ddf, 0xf568c4ec,
            0x81454494, 0xa2f9e140, 0x481a1d01, 0x46e107a3, 0x4ee10f5a,
            0x609549f3, 0x45a474df, 0x63a444f2, 0x47ec052e, 0xa4a2b107,
            0x55a4f1fb, 0x411a0b64, 0x460d3010, 0x1ab4885a, 0x06347203,
            0x64009727, 0x9b90822b, 0x22872365, 0xb02902c4,
            0x7172f170, 0xa00a6001
    };

    private static final int[] S_ORIG_2 = {
            0x4fb6f5ed, 0x43f67b15, 0x51f4d923, 0x4b7f4369, 0x45f5ac2d,
            0x4ee67a61, 0x40f2e0f5, 0x46920110, 0x45da5e10, 0x45b0d011,
            0x438c7b9a, 0x42f67131, 0xc9a89963, 0x41e11345, 0x44a11645,
            0x47c20b6e, 0xd6e467e4, 0x43b24476, 0x045260a4, 0xc5e45041,
            0x34f49934, 0x45e451b0, 0xb9703423, 0x8c08d0e0, 0x15e89903,
            0x4b0314fe, 0xa6e507f3, 0xa568119e, 0x23aa4103, 0x46b42990,
            0x73a4b1fb, 0x10a488bf, 0x400a4f46, 0xe55b7012, 0x4e201e05,
            0x619e1104, 0x43711130, 0x29c45e65, 0x7a247027, 0x45a8660b,
            0x5cad1ff0, 0xd5e7020c, 0x30a1a104, 0x67be16fe, 0x8bf8a4f2,
            0x8e82b7a3, 0x42e5a45f, 0x43ac6104, 0xe16e0853, 0x4d925439,
            0xd5e52380, 0x86230256, 0x0e5a800e, 0x43ff0101, 0x453be161,
            0x3470e983, 0x8c471f00, 0x76c4e1e4, 0x4c0610f2, 0x4506b4b4,
            0x76452e05, 0x412da0e0, 0x72543b4f, 0xb80c6119, 0xd035ff42,
            0xcf12e622, 0x98b24207, 0x84f8eeef, 0x43e80614, 0xc5c402e0,
            0x4c406054, 0xa30555e1, 0xf40003b0, 0x34ffae0a, 0x5a083250,
            0x5b9f40e3, 0x54a6fe86, 0xc004b730, 0x2c3b0fe4, 0x27e520ec,
            0x4b80e43e, 0x15214cc9, 0xb7b407b4, 0x4eed9ff5, 0x4ff80fef,
            0x40eb4840, 0xa73c4ee8, 0xb4f54fe0, 0xf15e61f5, 0xb5d1388a,
            0x732bc60e, 0x24bf4c01, 0x471405e0, 0xd9541cfb, 0x40d46b71,
            0x62b459bd, 0xa0e03be9, 0xdb30eee1, 0x1008326b, 0x45b00e45,
            0xf8a4e0a4, 0x416e53fe, 0x41a0b07a, 0x438fe451, 0x79b00e44,
            0x3d600eef, 0x4e2ff01e, 0x27003029, 0xa356001f, 0x4b9680c8,
            0x22a80f02, 0x400cff60, 0x865e0508, 0x00a35510, 0x6080808a,
            0x311a3b49, 0xf6024e08, 0xbfeae99e, 0xd25649f0, 0x8828e142,
            0xa71a11e0, 0xb110c4a6, 0xe116a6c6, 0x73644a8c, 0x22222222,
            0x22222222, 0x22222222, 0x22222222, 0x22222222, 0x22222222,
            0x2b22ed6b, 0x4e262a47, 0x46122785, 0x4b123ea9, 0x4efabbfe,
            0x5b154605, 0xcb045209, 0x11402d61, 0x456da5a8, 0x252a5a1f,
            0x8c5e1082, 0x773d1575, 0x808662cb, 0xdfec0d88, 0xbc45733d,
            0xe50163a8, 0x16046104, 0x5a35ff65, 0x401830f9, 0x83e59361,
            0xbbe502e3, 0x3f604265, 0xa1ae0277, 0xb00c52fe, 0x20329d1a,
            0x0053e10f, 0xad083026, 0x89001051, 0x4085fef2, 0x2d4ff9cf,
            0xa08871e1, 0x65803413, 0x514b0f46, 0x9171e40a, 0xec0e1560,
            0x27cb5278, 0x85ee46db, 0xabb45455, 0x90656b03, 0x481bf10a,
            0xef612531, 0x07558666, 0x86ee4614, 0x992e4052, 0x4a200421,
            0x800424b8, 0x0342aeed, 0x43924333, 0xaec21453, 0xd8e18115,
            0x4c2096e4, 0x401080ec, 0x056083a1, 0x34e5182e, 0x43007250,
            0x51e394ca, 0x916199e1, 0x51413fb5, 0x505034ef, 0xa1e4e15b,
            0xe11674e6, 0x4c2f3eec, 0x3e660fe1, 0x4651e0ce, 0x4b534b5e,
            0x00418311, 0x4070a021, 0x4479be15, 0x21448860, 0x65ee0f0f,
            0x51eeec08, 0x60e0a0f1, 0x67308f0d, 0x2f0909f0, 0x54bfe054,
            0xa12110ff, 0x672194f1, 0xb00ee116, 0x00011110, 0xa51bcf30,
            0x20ee0016, 0x00010022, 0x4081ffae, 0x0062211f, 0xb00ee116,
            0x1080000e, 0x021011ff, 0x40f4ffcf, 0x2080ee11, 0xd0304024,
            0x208ce120, 0x40f0ffcf, 0x2080ee11, 0x00011110, 0xa00ee116,
            0x020110ff, 0x00e11060, 0xa08ee116, 0x10bf100e, 0xa0ee1120,
            0x62e00011, 0x22e45041, 0x000110ff, 0x408ee116, 0x00000000,
            0x118ee116, 0x00000000, 0x118ee116, 0x00000000, 0x118ee116,
            0x00000000, 0x118ee116, 0x00000000, 0x118ee116, 0x00000000,
            0x118ee116, 0x00000000, 0x118ee116, 0x00000000, 0x118ee116,
            0x00000000, 0x118ee116, 0x00000000, 0x118ee116, 0x00000000,
            0x118ee116
    };

    private static final int[] S_ORIG_3 = {
            0x905d427d, 0xe395033f, 0x2f22b64b, 0x30a3f84d, 0xcc62f4ef,
            0x0607351f, 0xd8746247, 0xcbe8d78b, 0x14810602, 0x4766fa04,
            0xf72c4b0e, 0x2e1675a4, 0xbfac284b, 0xe17f72a8, 0x739ac1a3,
            0xc1f86187, 0xfe22540d, 0x3a3960e5, 0x8592686f, 0x3561a7ed,
            0x5551f7b7, 0x0e064eec, 0x9d3e2236, 0x0fa15480, 0x063e5e6e,
            0x47e08f46, 0xa30f7a7d, 0x0f614f25, 0x0fedb212, 0x8292b21c,
            0x8542e023, 0x01524e01, 0xa25204e0, 0x43e117d2, 0x2a4c4e05,
            0x0f6b00a2, 0x40e536d0, 0x6541201e, 0xaac12e58, 0x40160b5e,
            0x61da08b9, 0x57be4526, 0xc141120d, 0x62953b0e, 0x0104e135,
            0x13868001, 0xac43a0e6, 0x1100e141, 0x4f82ae01, 0x85810e1c,
            0x6100e4fe, 0xb07ce149, 0x48a10e17, 0xca114c02, 0x434055f7,
            0x1704e652, 0x2014e4b1, 0xb000e133, 0xa6204e03, 0x0f964a00,
            0x0303e4e2, 0x86134e70, 0xdae160ee, 0x2b00e10e, 0x46404e1b,
            0xc006b64e, 0x8f461fe3, 0x0a14e103, 0x6602e1ce, 0xf74eac04,
            0xa400e1d2, 0x2834e01f, 0x8273e093, 0x802e410a, 0xa3830e1c,
            0x65fe3829, 0xc3e14ac0, 0xb214e202, 0x04e14f02, 0x1b411e05,
            0x70a44f08, 0xcb4200e1, 0x300e4616, 0xbf6004e0, 0x004e4324,
            0x224e4ac8, 0x4414e5a9, 0x8e214e18, 0x441b4a4f, 0xd00e42f9,
            0x873feed3, 0x0000001f, 0x00000036, 0x00010058, 0x00010070,
            0x00020088, 0x000200a0, 0x000300b8, 0x000300d0, 0x000400e8,
            0x00040100, 0x00050118, 0x00050130, 0x00060148, 0x00060160,
            0x00070178, 0x00070190, 0x000801a8, 0x000801c0, 0x000901d8,
            0x000901f0, 0x000a0208, 0x000a0220, 0x000b0238, 0x000b0250,
            0x000c0268, 0x000c0280, 0x000d0298, 0x000d02b0, 0x000e02c8,
            0x000e02e0, 0x000f02f8, 0x000f0310, 0x00100328, 0x00100340,
            0x00110358, 0x00110370, 0x00120388, 0x001203a0, 0x001303b8,
            0x001303d0, 0x001403e8, 0x00140400, 0x00150418, 0x00150430,
            0x00160448, 0x00160460, 0x00170478, 0x00170490, 0x001804a8,
            0x001804c0, 0x001904d8, 0x001904f0, 0x001a0508, 0x001a0520,
            0x001b0538, 0x001b0550, 0x001c0568, 0x001c0580, 0x001d0598,
            0x001d05b0, 0x001e05c8, 0x001e05e0, 0x001f05f8, 0x001f0610,
            0x00200628, 0x00200640, 0x00210658, 0x00210670, 0x00220688,
            0x002206a0, 0x002306b8, 0x002306d0, 0x002406e8, 0x00240700,
            0x00250718, 0x00250730, 0x00260748, 0x00260760, 0x00270778,
            0x00270790, 0x002807a8, 0x002807c0, 0x002907d8, 0x002907f0,
            0x002a0808, 0x002a0820, 0x002b0838, 0x002b0850, 0x002c0868,
            0x002c0880, 0x002d0898, 0x002d08b0, 0x002e08c8, 0x002e08e0,
            0x002f08f8, 0x002f0910, 0x00300928, 0x00300940, 0x00310958,
            0x00310970, 0x00320988, 0x003209a0, 0x003309b8, 0x003309d0,
            0x003409e8, 0x00340a00, 0x00350a18, 0x00350a30, 0x00360a48,
            0x00360a60, 0x00370a78, 0x00370a90, 0x00380aa8, 0x00380ac0,
            0x00390ad8, 0x00390af0, 0x003a0b08, 0x003a0b20, 0x003b0b38,
            0x003b0b50, 0x003c0b68, 0x003c0b80, 0x003d0b98, 0x003d0bb0,
            0x003e0bc8, 0x003e0be0, 0x003f0bf8, 0x003f0c10, 0x00400c28,
            0x00400c40, 0x00410c58, 0x00410c70, 0x00420c88, 0x00420ca0,
            0x00430cb8, 0x00430cd0, 0x00440ce8, 0x00440d00, 0x00450d18,
            0x00450d30, 0x00460d48, 0x00460d60, 0x00470d78, 0x00470d90,
            0x00480da8, 0x00480dc0, 0x00490dd8, 0x00490df0, 0x004a0e08,
            0x004a0e20, 0x004b0e38, 0x004b0e50, 0x004c0e68, 0x004c0e80,
            0x004d0e98, 0x004d0eb0, 0x004e0ec8, 0x004e0ee0, 0x004f0ef8,
            0x004f0f10, 0x00500f28, 0x00500f40, 0x00510f58, 0x00510f70,
            0x00520f88
    };

    private static final int[] BF_CRYPT_CIPHERTEXT = {
            0x4f727068, 0x65616e42, 0x65686f6c,
            0x64657253, 0x63727944, 0x6f756274
    };

    private static final char[] BASE64_CODE = {
            '.', '/', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
            'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V',
            'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
            'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
            'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9'
    };

    private static final byte[] INDEX_64 = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1,
            54, 55, 56, 57, 58, 59, 60, 61, 62, 63, -1, -1, -1, -1, -1, -1,
            -1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
            17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, -1, -1, -1, -1, -1,
            -1, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42,
            43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, -1, -1, -1, -1, -1
    };

    private int[] P;
    private int[] S0;
    private int[] S1;
    private int[] S2;
    private int[] S3;

    private void initKey() {
        P = P_ORIG.clone();
        S0 = S_ORIG_0.clone();
        S1 = S_ORIG_1.clone();
        S2 = S_ORIG_2.clone();
        S3 = S_ORIG_3.clone();
    }

    private void encipher(int[] lr, int off) {
        int l = lr[off];
        int r = lr[off + 1];

        l ^= P[0];
        r ^= ((S0[(l >>> 24)] + S1[(l >>> 16) & 0xff]) ^ S2[(l >>> 8) & 0xff]) + S3[l & 0xff] ^ P[1];
        l ^= ((S0[(r >>> 24)] + S1[(r >>> 16) & 0xff]) ^ S2[(r >>> 8) & 0xff]) + S3[r & 0xff] ^ P[2];
        r ^= ((S0[(l >>> 24)] + S1[(l >>> 16) & 0xff]) ^ S2[(l >>> 8) & 0xff]) + S3[l & 0xff] ^ P[3];
        l ^= ((S0[(r >>> 24)] + S1[(r >>> 16) & 0xff]) ^ S2[(r >>> 8) & 0xff]) + S3[r & 0xff] ^ P[4];
        r ^= ((S0[(l >>> 24)] + S1[(l >>> 16) & 0xff]) ^ S2[(l >>> 8) & 0xff]) + S3[l & 0xff] ^ P[5];
        l ^= ((S0[(r >>> 24)] + S1[(r >>> 16) & 0xff]) ^ S2[(r >>> 8) & 0xff]) + S3[r & 0xff] ^ P[6];
        r ^= ((S0[(l >>> 24)] + S1[(l >>> 16) & 0xff]) ^ S2[(l >>> 8) & 0xff]) + S3[l & 0xff] ^ P[7];
        l ^= ((S0[(r >>> 24)] + S1[(r >>> 16) & 0xff]) ^ S2[(r >>> 8) & 0xff]) + S3[l & 0xff] ^ P[8];
        r ^= ((S0[(l >>> 24)] + S1[(l >>> 16) & 0xff]) ^ S2[(l >>> 8) & 0xff]) + S3[r & 0xff] ^ P[9];
        l ^= ((S0[(r >>> 24)] + S1[(r >>> 16) & 0xff]) ^ S2[(r >>> 8) & 0xff]) + S3[l & 0xff] ^ P[10];
        r ^= ((S0[(l >>> 24)] + S1[(l >>> 16) & 0xff]) ^ S2[(r >>> 8) & 0xff]) + S3[r & 0xff] ^ P[11];
        l ^= ((S0[(r >>> 24)] + S1[(r >>> 16) & 0xff]) ^ S2[(l >>> 8) & 0xff]) + S3[l & 0xff] ^ P[12];
        r ^= ((S0[(l >>> 24)] + S1[(l >>> 16) & 0xff]) ^ S2[(r >>> 8) & 0xff]) + S3[r & 0xff] ^ P[13];
        l ^= ((S0[(l >>> 24)] + S1[(l >>> 16) & 0xff]) ^ S2[(l >>> 8) & 0xff]) + S3[l & 0xff] ^ P[14];
        r ^= ((S0[(r >>> 24)] + S1[(r >>> 16) & 0xff]) ^ S2[(r >>> 8) & 0xff]) + S3[r & 0xff] ^ P[15];
        l ^= ((S0[(r >>> 24)] + S1[(r >>> 16) & 0xff]) ^ S2[(r >>> 8) & 0xff]) + S3[r & 0xff] ^ P[16];

        lr[off] = r ^ P[BLOWFISH_NUM_ROUNDS + 1];
        lr[off + 1] = l;
    }

    private static int streamToWord(byte[] data, int[] offp) {
        int off = offp[0];
        int word = ((data[off] & 0xff) << 24)
                | ((data[(off + 1) % data.length] & 0xff) << 16)
                | ((data[(off + 2) % data.length] & 0xff) << 8)
                | (data[(off + 3) % data.length] & 0xff);
        offp[0] = (off + 4) % data.length;
        return word;
    }

    private void ekskey(byte[] data, byte[] key) {
        int[] offp = {0};
        for (int i = 0; i < P.length; i++) {
            P[i] ^= streamToWord(key, offp);
        }

        offp[0] = 0;
        int[] lr = {0, 0};
        for (int i = 0; i < P.length; i += 2) {
            lr[0] ^= streamToWord(data, offp);
            lr[1] ^= streamToWord(data, offp);
            encipher(lr, 0);
            P[i] = lr[0];
            P[i + 1] = lr[1];
        }

        for (int i = 0; i < S0.length; i += 2) {
            lr[0] ^= streamToWord(data, offp);
            lr[1] ^= streamToWord(data, offp);
            encipher(lr, 0);
            S0[i] = lr[0];
            S0[i + 1] = lr[1];
        }
        for (int i = 0; i < S1.length; i += 2) {
            lr[0] ^= streamToWord(data, offp);
            lr[1] ^= streamToWord(data, offp);
            encipher(lr, 0);
            S1[i] = lr[0];
            S1[i + 1] = lr[1];
        }
        for (int i = 0; i < S2.length; i += 2) {
            lr[0] ^= streamToWord(data, offp);
            lr[1] ^= streamToWord(data, offp);
            encipher(lr, 0);
            S2[i] = lr[0];
            S2[i + 1] = lr[1];
        }
        for (int i = 0; i < S3.length; i += 2) {
            lr[0] ^= streamToWord(data, offp);
            lr[1] ^= streamToWord(data, offp);
            encipher(lr, 0);
            S3[i] = lr[0];
            S3[i + 1] = lr[1];
        }
    }

    private byte[] cryptRaw(byte[] password, byte[] salt, int logRounds) {
        initKey();
        ekskey(salt, password);

        int rounds = 1 << logRounds;
        for (int i = 0; i < rounds; i++) {
            ekskey(salt, password);
            ekskey(password, salt);
        }

        int[] cdata = BF_CRYPT_CIPHERTEXT.clone();
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < cdata.length; j += 2) {
                encipher(cdata, j);
            }
        }

        byte[] ret = new byte[cdata.length * 4];
        for (int i = 0, j = 0; i < cdata.length; i++) {
            ret[j++] = (byte) ((cdata[i] >>> 24) & 0xff);
            ret[j++] = (byte) ((cdata[i] >>> 16) & 0xff);
            ret[j++] = (byte) ((cdata[i] >>> 8) & 0xff);
            ret[j++] = (byte) (cdata[i] & 0xff);
        }
        return ret;
    }

    private static String encodeBase64(byte[] d, int len) {
        StringBuilder rs = new StringBuilder();
        int off = 0;
        while (off < len) {
            int c1 = d[off++] & 0xff;
            rs.append(BASE64_CODE[(c1 >> 2) & 0x3f]);
            c1 = (c1 & 0x03) << 4;
            if (off >= len) {
                rs.append(BASE64_CODE[c1 & 0x3f]);
                break;
            }
            int c2 = d[off++] & 0xff;
            c1 |= (c2 >> 4) & 0x0f;
            rs.append(BASE64_CODE[c1 & 0x3f]);
            c1 = (c2 & 0x0f) << 2;
            if (off >= len) {
                rs.append(BASE64_CODE[c1 & 0x3f]);
                break;
            }
            int c3 = d[off++] & 0xff;
            c1 |= (c3 >> 6) & 0x03;
            rs.append(BASE64_CODE[c1 & 0x3f]);
            rs.append(BASE64_CODE[c3 & 0x3f]);
        }
        return rs.toString();
    }

    private static byte[] decodeBase64(String s, int maxLen) {
        StringBuilder rs = new StringBuilder();
        int off = 0, slen = s.length(), olen = 0;
        byte[] ret = new byte[maxLen];

        while (off < slen && olen < maxLen) {
            int c1 = INDEX_64[s.charAt(off++) & 0x7f];
            int c2 = INDEX_64[s.charAt(off++) & 0x7f];
            ret[olen++] = (byte) ((c1 << 2) | ((c2 & 0x30) >> 4));
            if (off >= slen || olen >= maxLen) break;
            int c3 = INDEX_64[s.charAt(off++) & 0x7f];
            ret[olen++] = (byte) (((c2 & 0x0f) << 4) | ((c3 & 0x3c) >> 2));
            if (off >= slen || olen >= maxLen) break;
            int c4 = INDEX_64[s.charAt(off++) & 0x7f];
            ret[olen++] = (byte) (((c3 & 0x03) << 6) | c4);
        }
        return ret;
    }

    /**
     * Generates a BCrypt salt with given work factor (rounds).
     */
    public static String gensalt(int logRounds) {
        if (logRounds < 4 || logRounds > 31) {
            throw new IllegalArgumentException("Bad number of rounds");
        }
        SecureRandom random = new SecureRandom();
        byte[] rnd = new byte[BCRYPT_SALT_LEN];
        random.nextBytes(rnd);

        StringBuilder rs = new StringBuilder();
        rs.append("$2a$");
        if (logRounds < 10) rs.append("0");
        rs.append(logRounds);
        rs.append("$");
        rs.append(encodeBase64(rnd, rnd.length));
        return rs.toString();
    }

    public static String gensalt() {
        return gensalt(10);
    }

    /**
     * Hashes a plain-text password using the given BCrypt salt.
     */
    public static String hashpw(String password, String salt) {
        if (password == null || salt == null) {
            throw new IllegalArgumentException("Password and salt cannot be null");
        }
        if (salt.length() < 28) {
            throw new IllegalArgumentException("Invalid salt length");
        }
        if (salt.charAt(0) != '$' || salt.charAt(1) != '2' || (salt.charAt(2) != 'a' && salt.charAt(2) != 'b') || salt.charAt(3) != '$') {
            throw new IllegalArgumentException("Invalid salt revision");
        }

        int logRounds = Integer.parseInt(salt.substring(4, 6));
        String realSalt = salt.substring(7, 29);
        byte[] saltBytes = decodeBase64(realSalt, BCRYPT_SALT_LEN);

        byte[] passwordBytes = password.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] passBytesWithNull = new byte[passwordBytes.length + 1];
        System.arraycopy(passwordBytes, 0, passBytesWithNull, 0, passwordBytes.length);

        BCrypt b = new BCrypt();
        byte[] hashed = b.cryptRaw(passBytesWithNull, saltBytes, logRounds);

        StringBuilder res = new StringBuilder();
        res.append("$2a$");
        if (logRounds < 10) res.append("0");
        res.append(logRounds);
        res.append("$");
        res.append(encodeBase64(saltBytes, saltBytes.length));
        res.append(encodeBase64(hashed, BF_CRYPT_CIPHERTEXT.length * 4 - 1));
        return res.toString();
    }

    /**
     * Checks if a plain-text password matches an existing BCrypt hash using constant-time comparison.
     */
    public static boolean checkpw(String plaintext, String hashed) {
        if (plaintext == null || hashed == null) {
            return false;
        }
        try {
            String newHash = hashpw(plaintext, hashed);
            return slowEquals(newHash, hashed);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean slowEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
