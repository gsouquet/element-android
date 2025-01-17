/*
 * Copyright (c) 2022 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.home.room.detail.timeline.render

import android.annotation.StringRes
import im.vector.app.R
import im.vector.app.test.fakes.FakeActiveSessionHolder
import im.vector.app.test.fakes.FakeStringProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.amshove.kluent.shouldBeEqualTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.matrix.android.sdk.api.session.events.model.Event
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.events.model.getPollQuestion
import org.matrix.android.sdk.api.session.events.model.isAudioMessage
import org.matrix.android.sdk.api.session.events.model.isFileMessage
import org.matrix.android.sdk.api.session.events.model.isImageMessage
import org.matrix.android.sdk.api.session.events.model.isLiveLocation
import org.matrix.android.sdk.api.session.events.model.isPoll
import org.matrix.android.sdk.api.session.events.model.isSticker
import org.matrix.android.sdk.api.session.events.model.isVideoMessage
import org.matrix.android.sdk.api.session.events.model.isVoiceMessage
import org.matrix.android.sdk.api.session.room.model.relation.ReplyToContent
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent

private const val A_ROOM_ID = "room-id"
private const val AN_EVENT_ID = "event-id"
private const val A_REPLY_TO_EVENT_MATRIX_FORMATTED_BODY =
        "<mx-reply>" +
                "<blockquote>" +
                "<a href=\"matrixToLink\">In reply to</a> " +
                "<a href=\"matrixToLink\">@user:matrix.org</a>" +
                "<br />" +
                "Message content" +
                "</blockquote>" +
                "</mx-reply>" +
                "Reply text"
private const val A_NEW_PREFIX = "new-prefix"
private const val A_NEW_CONTENT = "new-content"
private const val PREFIX_PROCESSED_ONLY_REPLY_TO_EVENT_MATRIX_FORMATTED_BODY =
        "<mx-reply>" +
                "<blockquote>" +
                "<a href=\"matrixToLink\">$A_NEW_PREFIX</a> " +
                "<a href=\"matrixToLink\">@user:matrix.org</a>" +
                "<br />" +
                "Message content" +
                "</blockquote>" +
                "</mx-reply>" +
                "Reply text"
private const val FULLY_PROCESSED_REPLY_TO_EVENT_MATRIX_FORMATTED_BODY =
        "<mx-reply>" +
                "<blockquote>" +
                "<a href=\"matrixToLink\">$A_NEW_PREFIX</a> " +
                "<a href=\"matrixToLink\">@user:matrix.org</a>" +
                "<br />" +
                A_NEW_CONTENT +
                "</blockquote>" +
                "</mx-reply>" +
                "Reply text"

class ProcessBodyOfReplyToEventUseCaseTest {

    private val fakeActiveSessionHolder = FakeActiveSessionHolder()
    private val fakeStringProvider = FakeStringProvider()
    private val fakeReplyToContent = ReplyToContent(eventId = AN_EVENT_ID)
    private val fakeRepliedEvent = givenARepliedEvent()

    private val processBodyOfReplyToEventUseCase = ProcessBodyOfReplyToEventUseCase(
            activeSessionHolder = fakeActiveSessionHolder.instance,
            stringProvider = fakeStringProvider.instance,
    )

    @Before
    fun setup() {
        givenNewPrefix()
        mockkStatic("org.matrix.android.sdk.api.session.events.model.EventKt")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `given a replied event of type file message when process the formatted body then content is replaced by correct string`() {
        // Given
        givenTypeOfRepliedEvent(isFileMessage = true)
        givenNewContentForId(R.string.message_reply_to_sender_sent_file)

        executeAndAssertResult()
    }

    @Test
    fun `given a replied event of type voice message when process the formatted body then content is replaced by correct string`() {
        // Given
        givenTypeOfRepliedEvent(isVoiceMessage = true)
        givenNewContentForId(R.string.message_reply_to_sender_sent_voice_message)

        executeAndAssertResult()
    }

    @Test
    fun `given a replied event of type audio message when process the formatted body then content is replaced by correct string`() {
        // Given
        givenTypeOfRepliedEvent(isAudioMessage = true)
        givenNewContentForId(R.string.message_reply_to_sender_sent_audio_file)

        executeAndAssertResult()
    }

    @Test
    fun `given a replied event of type image message when process the formatted body then content is replaced by correct string`() {
        // Given
        givenTypeOfRepliedEvent(isImageMessage = true)
        givenNewContentForId(R.string.message_reply_to_sender_sent_image)

        executeAndAssertResult()
    }

    @Test
    fun `given a replied event of type video message when process the formatted body then content is replaced by correct string`() {
        // Given
        givenTypeOfRepliedEvent(isVideoMessage = true)
        givenNewContentForId(R.string.message_reply_to_sender_sent_video)

        executeAndAssertResult()
    }

    @Test
    fun `given a replied event of type sticker message when process the formatted body then content is replaced by correct string`() {
        // Given
        givenTypeOfRepliedEvent(isStickerMessage = true)
        givenNewContentForId(R.string.message_reply_to_sender_sent_sticker)

        executeAndAssertResult()
    }

    @Test
    fun `given a replied event of type poll message with null question when process the formatted body then content is replaced by correct string`() {
        // Given
        givenTypeOfRepliedEvent(isPollMessage = true)
        givenNewContentForId(R.string.message_reply_to_sender_created_poll)
        every { fakeRepliedEvent.getClearType() } returns EventType.POLL_START.unstable
        every { fakeRepliedEvent.getPollQuestion() } returns null

        executeAndAssertResult()
    }

    @Test
    fun `given a replied event of type poll message with existing question when process the formatted body then content is replaced by correct string`() {
        // Given
        givenTypeOfRepliedEvent(isPollMessage = true)
        givenNewContentForId(R.string.message_reply_to_sender_created_poll)
        every { fakeRepliedEvent.getClearType() } returns EventType.POLL_START.unstable
        every { fakeRepliedEvent.getPollQuestion() } returns A_NEW_CONTENT

        executeAndAssertResult()
    }

    @Test
    fun `given a replied event of type poll end message when process the formatted body then content is replaced by correct string`() {
        // Given
        givenTypeOfRepliedEvent(isPollMessage = true)
        givenNewContentForId(R.string.message_reply_to_sender_ended_poll)
        every { fakeRepliedEvent.getClearType() } returns EventType.POLL_END.unstable
        every { fakeRepliedEvent.getPollQuestion() } returns null

        executeAndAssertResult()
    }

    @Test
    fun `given a replied event of type live location message when process the formatted body then content is replaced by correct string`() {
        // Given
        givenTypeOfRepliedEvent(isLiveLocationMessage = true)
        givenNewContentForId(R.string.live_location_description)

        executeAndAssertResult()
    }

    @Test
    fun `given a replied event of type not handled when process the formatted body only prefix is replaced by correct string`() {
        // Given
        givenTypeOfRepliedEvent()

        // When
        val result = processBodyOfReplyToEventUseCase.execute(
                roomId = A_ROOM_ID,
                matrixFormattedBody = A_REPLY_TO_EVENT_MATRIX_FORMATTED_BODY,
                replyToContent = fakeReplyToContent,
        )

        // Then
        result shouldBeEqualTo PREFIX_PROCESSED_ONLY_REPLY_TO_EVENT_MATRIX_FORMATTED_BODY
    }

    @Test
    fun `given no replied event found when process the formatted body then only prefix is replaced by correct string`() {
        // Given
        givenARepliedEvent(timelineEvent = null)

        // When
        val result = processBodyOfReplyToEventUseCase.execute(
                roomId = A_ROOM_ID,
                matrixFormattedBody = A_REPLY_TO_EVENT_MATRIX_FORMATTED_BODY,
                replyToContent = fakeReplyToContent,
        )

        // Then
        result shouldBeEqualTo PREFIX_PROCESSED_ONLY_REPLY_TO_EVENT_MATRIX_FORMATTED_BODY
    }

    private fun executeAndAssertResult() {
        // When
        val result = processBodyOfReplyToEventUseCase.execute(
                roomId = A_ROOM_ID,
                matrixFormattedBody = A_REPLY_TO_EVENT_MATRIX_FORMATTED_BODY,
                replyToContent = fakeReplyToContent,
        )

        // Then
        result shouldBeEqualTo FULLY_PROCESSED_REPLY_TO_EVENT_MATRIX_FORMATTED_BODY
    }

    private fun givenARepliedEvent(timelineEvent: TimelineEvent? = mockk()): Event {
        val event = mockk<Event>()
        timelineEvent?.let { every { it.root } returns event }
        fakeActiveSessionHolder
                .fakeSession
                .roomService()
                .getRoom(A_ROOM_ID)
                .timelineService()
                .givenTimelineEvent(timelineEvent)
        return event
    }

    private fun givenTypeOfRepliedEvent(
            isFileMessage: Boolean = false,
            isVoiceMessage: Boolean = false,
            isAudioMessage: Boolean = false,
            isImageMessage: Boolean = false,
            isVideoMessage: Boolean = false,
            isStickerMessage: Boolean = false,
            isPollMessage: Boolean = false,
            isLiveLocationMessage: Boolean = false,
    ) {
        every { fakeRepliedEvent.isFileMessage() } returns isFileMessage
        every { fakeRepliedEvent.isVoiceMessage() } returns isVoiceMessage
        every { fakeRepliedEvent.isAudioMessage() } returns isAudioMessage
        every { fakeRepliedEvent.isImageMessage() } returns isImageMessage
        every { fakeRepliedEvent.isVideoMessage() } returns isVideoMessage
        every { fakeRepliedEvent.isSticker() } returns isStickerMessage
        every { fakeRepliedEvent.isPoll() } returns isPollMessage
        every { fakeRepliedEvent.isLiveLocation() } returns isLiveLocationMessage
    }

    private fun givenNewPrefix() {
        fakeStringProvider.given(R.string.message_reply_to_prefix, A_NEW_PREFIX)
    }

    private fun givenNewContentForId(@StringRes resId: Int) {
        fakeStringProvider.given(resId, A_NEW_CONTENT)
    }
}
