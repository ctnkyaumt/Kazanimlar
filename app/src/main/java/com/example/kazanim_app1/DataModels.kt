package com.example.kazanim_app1

/** Data models **/
data class Entry(val head1: String?, val head2: String?, val text: String?)
data class Section(val name: String, val entries: List<Entry>)
data class SubMenu(var name: String, var sections: List<Section> = emptyList())
