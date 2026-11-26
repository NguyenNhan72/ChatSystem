package com.example.chatsystem.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.chatsystem.data.local.dao.ConversationDao
import com.example.chatsystem.data.local.dao.MessageDao
import com.example.chatsystem.data.local.entity.ConversationEntity
import com.example.chatsystem.data.local.entity.MemberEntity
import com.example.chatsystem.data.local.entity.MessageEntity

@Database(
    entities = [ConversationEntity::class, MemberEntity::class, MessageEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "chat.db")
                // Don gian hoa: khi thay doi schema thi xoa cache cu tao lai (du lieu se tu dong sync lai tu server).
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
