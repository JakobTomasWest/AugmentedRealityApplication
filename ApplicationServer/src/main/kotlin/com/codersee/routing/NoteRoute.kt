package com.codersee.routing

import com.codersee.model.Note
import com.codersee.repository.NotesRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.noteRoute(notesRepository: NotesRepository){

    get(){
        val username = extractPrincipalUsername(call)
        if(username == null){
            call.respond(HttpStatusCode.Unauthorized)
        }
        call.respond(notesRepository.allNotesFor(username!!))
    }

    get("/{username}"){
        val reqName = call.parameters["username"]
        if(reqName == null){
            call.respond(HttpStatusCode.BadRequest)
        }
        val thisUser =  extractPrincipalUsername(call)
        if(thisUser == null){
            //I think this is impossible since this is in an auth{} block
            call.respond(HttpStatusCode.Unauthorized)
        }

        call.respond( if(thisUser.equals(reqName) )
                notesRepository.allNotesFor(reqName!!)
            else
                notesRepository.publicNotesFor(reqName!!)
        )

    }

    post(){
        val thisUser = extractPrincipalUsername(call)!!
        val note = call.receive<Note>()
        notesRepository.postNote(thisUser, note)
        call.respond(HttpStatusCode.Created)

    }
}