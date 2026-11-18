package com.example.todolist.core

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.util.prefs.Preferences

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session")

class SessionManager (private val context: Context){
    companion object{
        val USER_ID = stringPreferenceskey("user_id")
        val USER_NAME = stringPreferenceskey("user_name")
        val IS_LOGGED_IN = booleanPreferenceskey("is_logges_in")
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { preferences -> preferences [IS_LOGGED_IN]?: false}
    val userId: Flow<String?> = context.dataStore.data.map { preferences -> preferences [USER_ID]}
    val userName: Flow<String?> = context.dataStore.data.map { preferences -> preferences [USER_NAME]}

    suspend fun saveSession(
        userId: String,
        userName: String,
    ){
        context.dataStore.edit{preferences ->
            preferences[USER_ID] = userId
            preferences[USER_NAME] = userName
            preferences[IS_LOGGED_IN] = true
        }
    }
    suspend fun logout(){
        context.dataStore.edit{preferences ->
            preferences.clear()
        }
    }
}