package com.uabea.mobile.core;

import java.util.HashMap;

/** Tabel string umum Unity (offset tetap) seperti di AssetStudio/UABEA. */
final class CommonStrings {
    private static final HashMap<Integer, String> MAP = new HashMap<>();

    static {
        MAP.put(0, "AABB");
        MAP.put(5, "AnimationClip");
        MAP.put(19, "AnimationCurve");
        MAP.put(34, "AnimationState");
        MAP.put(49, "Array");
        MAP.put(55, "Base");
        MAP.put(60, "BitField");
        MAP.put(69, "bitset");
        MAP.put(76, "bool");
        MAP.put(81, "char");
        MAP.put(86, "ColorRGBA");
        MAP.put(96, "Component");
        MAP.put(106, "data");
        MAP.put(111, "deque");
        MAP.put(117, "double");
        MAP.put(124, "dynamic_array");
        MAP.put(138, "FastPropertyName");
        MAP.put(155, "first");
        MAP.put(161, "float");
        MAP.put(167, "Font");
        MAP.put(172, "GameObject");
        MAP.put(183, "Generic Mono");
        MAP.put(196, "GradientNEW");
        MAP.put(208, "GUID");
        MAP.put(213, "GUIStyle");
        MAP.put(222, "int");
        MAP.put(226, "list");
        MAP.put(231, "long long");
        MAP.put(241, "map");
        MAP.put(245, "Matrix4x4f");
        MAP.put(256, "MdFour");
        MAP.put(263, "MonoBehaviour");
        MAP.put(277, "MonoScript");
        MAP.put(288, "m_ByteSize");
        MAP.put(299, "m_Curve");
        MAP.put(307, "m_EditorClassIdentifier");
        MAP.put(331, "m_EditorHideFlags");
        MAP.put(349, "m_Enabled");
        MAP.put(359, "m_ExtensionPtr");
        MAP.put(374, "m_GameObject");
        MAP.put(387, "m_Index");
        MAP.put(395, "m_IsArray");
        MAP.put(405, "m_IsStatic");
        MAP.put(416, "m_MetaFlag");
        MAP.put(427, "m_Name");
        MAP.put(434, "m_ObjectHideFlags");
        MAP.put(452, "m_PrefabInternal");
        MAP.put(469, "m_PrefabParentObject");
        MAP.put(490, "m_Script");
        MAP.put(499, "m_StaticEditorFlags");
        MAP.put(519, "m_Type");
        MAP.put(526, "m_Version");
        MAP.put(536, "Object");
        MAP.put(543, "pair");
        MAP.put(548, "PPtr<Component>");
        MAP.put(564, "PPtr<GameObject>");
        MAP.put(581, "PPtr<Material>");
        MAP.put(596, "PPtr<MonoBehaviour>");
        MAP.put(616, "PPtr<MonoScript>");
        MAP.put(633, "PPtr<Object>");
        MAP.put(646, "PPtr<Prefab>");
        MAP.put(659, "PPtr<Sprite>");
        MAP.put(672, "PPtr<TextAsset>");
        MAP.put(688, "PPtr<Texture>");
        MAP.put(702, "PPtr<Texture2D>");
        MAP.put(718, "PPtr<Transform>");
        MAP.put(734, "Prefab");
        MAP.put(741, "Quaternionf");
        MAP.put(753, "Rectf");
        MAP.put(759, "RectInt");
        MAP.put(767, "RectOffset");
        MAP.put(778, "second");
        MAP.put(785, "set");
        MAP.put(789, "short");
        MAP.put(795, "size");
        MAP.put(800, "SInt16");
        MAP.put(807, "SInt32");
        MAP.put(814, "SInt64");
        MAP.put(821, "SInt8");
        MAP.put(827, "staticvector");
        MAP.put(840, "string");
        MAP.put(847, "TextAsset");
        MAP.put(857, "TextMesh");
        MAP.put(866, "Texture");
        MAP.put(874, "Texture2D");
        MAP.put(884, "Transform");
        MAP.put(894, "TypelessData");
        MAP.put(907, "UInt16");
        MAP.put(914, "UInt32");
        MAP.put(921, "UInt64");
        MAP.put(928, "UInt8");
        MAP.put(934, "unsigned int");
        MAP.put(947, "unsigned long long");
        MAP.put(966, "unsigned short");
        MAP.put(981, "vector");
        MAP.put(988, "Vector2f");
        MAP.put(997, "Vector3f");
        MAP.put(1006, "Vector4f");
        MAP.put(1015, "m_ScriptingClassIdentifier");
        MAP.put(1042, "Gradient");
        MAP.put(1051, "Type*");
        MAP.put(1057, "int2_storage");
        MAP.put(1070, "int3_storage");
        MAP.put(1083, "BoundsInt");
        MAP.put(1093, "m_CorrespondingSourceObject");
        MAP.put(1121, "m_PrefabInstance");
        MAP.put(1138, "m_PrefabAsset");
        MAP.put(1152, "FileSize");
        MAP.put(1161, "Hash128");
    }

    private CommonStrings() {}

    static String get(int off) {
        String s = MAP.get(off);
        return s != null ? s : String.valueOf(off);
    }
}
