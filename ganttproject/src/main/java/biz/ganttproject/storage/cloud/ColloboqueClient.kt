/*
Copyright 2022 BarD Software s.r.o., Anastasiia Postnikova

This file is part of GanttProject Cloud.

GanttProject is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

GanttProject is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with GanttProject.  If not, see <http://www.gnu.org/licenses/>.
*/
package biz.ganttproject.storage.cloud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import net.sourceforge.ganttproject.GPLogger
import net.sourceforge.ganttproject.storage.*
import net.sourceforge.ganttproject.undo.GPUndoListener
import net.sourceforge.ganttproject.undo.GPUndoManager
import java.util.*
import java.util.concurrent.Executors
import javax.swing.event.UndoableEditEvent

class ColloboqueClient(private val projectDatabase: ProjectDatabase, undoManager: GPUndoManager) {
  private val myBaseTxnCommitInfo = TxnCommitInfo(0)
  private var projectRefid: String? = null
  private val eventLoopScope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())
  private val channelScope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())
  private val internalChannel = Channel<List<XlogRecord>>()
  private val externalChannel = Channel<ServerResponse.CommitResponse>()

  init {
    undoManager.addUndoableEditListener(object: GPUndoListener {
      override fun undoableEditHappened(e: UndoableEditEvent) {
        sendProjectStateLogs()
      }

      override fun undoOrRedoHappened() {}
      override fun undoReset() {}
    })
    eventLoopScope.launch {
      runEventLoop(internalChannel, externalChannel)
    }
  }

  /**
   * We process updates generated by this GanttProject instance (internal updates) and updates coming from the
   * cloud (external updates) in a single thread.
   * Once we receive an internal update, we send the log to the server and stop processing further internal updates
   * until we hear a response.
   */
  private suspend fun runEventLoop(internalChannel: Channel<List<XlogRecord>>, externalChannel: Channel<ServerResponse.CommitResponse>) {
    var acceptInternal = true
    while (true) {
      LOG.debug("Next event loop cycle. Accept internal=$acceptInternal")
      if (acceptInternal) {
        select {
          internalChannel.onReceive { txns ->
            LOG.debug("Message from the internal channel")
            sendXlog(txns)
            // TODO: start timeout to report missing response in a few moments
            acceptInternal = false
            LOG.debug("We will stop accepting internal messages until we hear a response")
          }
          externalChannel.onReceive { response ->
            LOG.debug("Message from the EXTERNAL channel")
            receiveXlog(response)
            acceptInternal = true
          }
        }
      } else {
        val response = externalChannel.receive()
        LOG.debug("Message from the EXTERNAL channel")
        receiveXlog(response)
        acceptInternal = true
      }
    }
  }

  private fun sendXlog(txns: List<XlogRecord>) {
    webSocket.sendLogs(
      InputXlog(
        myBaseTxnCommitInfo.baseTxnId,
        // TODO: use real user id
        "userId",
        projectRefid!!,
        txns,
        myBaseTxnCommitInfo.generateTrackingCode()
      )
    )
  }

  private fun receiveXlog(response: ServerResponse.CommitResponse) {
    try {
      // Check if we received our own update.
      if (response.clientTrackingCode != myBaseTxnCommitInfo.trackingCode) {
        // This is not our own update so let's apply it.
        projectDatabase.applyUpdate(response.logRecords, response.baseTxnId, response.newBaseTxnId)
      } else {
        // We need to advance base txn id anyway.
        projectDatabase.applyUpdate(emptyList(), response.baseTxnId, response.newBaseTxnId)
      }
      myBaseTxnCommitInfo.update(response.baseTxnId, response.newBaseTxnId,)
    } catch (ex: Exception) {
      LOG.error("Failed to apply external update", exception = ex)
    }
  }

  fun attach(webSocket: WebSocketClient) {
    webSocket.onCommitResponseReceived { response  -> this.fireXlogReceived(response) }
  }

  fun start(projectRefid: String, baseTxnId: BaseTxnId) {
    this.projectRefid = projectRefid
    onBaseTxnIdReceived(baseTxnId)
    this.projectDatabase.startLog(baseTxnId)
  }

  private fun fireXlogReceived(response: ServerResponse.CommitResponse) {
    channelScope.launch {
      externalChannel.send(response)
    }
  }

  private fun onBaseTxnIdReceived(baseTxnId: BaseTxnId) {
    myBaseTxnCommitInfo.update(0, baseTxnId)
  }

  private fun sendProjectStateLogs() {
    LOG.debug("Sending project state logs")
    try {
      val txns = projectDatabase.outgoingTransactions
      if (txns.isNotEmpty()) {
        channelScope.launch {
          internalChannel.send(txns)
        }
      }
    } catch (e: ProjectDatabaseException) {
      LOG.error("Failed to send logs", exception = e)
    }
  }

}


/**
 * Data for transition from one synced state to another. When client is connected, it expects new updates to be applied
 * to the state produced by baseTxnId. Also, to distinguish between updates generated by this client and by other clients,
 * we check update tracking code.
 */
private class TxnCommitInfo(var baseTxnId: BaseTxnId) {
  var trackingCode: String = ""
    private set

  fun update(oldTxnId: BaseTxnId, newTxnId: BaseTxnId) {
    if (oldTxnId != baseTxnId) {
      LOG.error("Unexpected value of oldTxnId={}, expected {}", oldTxnId, baseTxnId)
      throw IllegalStateException("Unexpected update from the server")
    }
    baseTxnId = newTxnId
  }

  fun generateTrackingCode(): String =
    UUID.randomUUID().toString().replace("-", "").also {
      trackingCode = it
    }
}

private val LOG = GPLogger.create("Cloud.RealTimeSync")