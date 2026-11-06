/**
 * NOTICE: This file was AI-assisted. The verbatim prompt for this work is recorded in
 * ../../AI_Disclosure/Wyatt_AI_Use.md.
 *
 * Bare-bones implementation of US 02.05.01: after a lottery's registration period ends
 * the winners are drawn and notified without opening the app and without any owner action.
 * The design and the no-Blaze investigation are documented in
 * ../../../documentation/AutomaticDraws.md; the wire fields here mirror the Kotlin data
 * classes exactly (models/Lottery.kt, models/WaitingListEntry.kt, models/Notification.kt).
 */

import { firebase } from 'firebase/firestore';
import { initialize } from 'firebase-admin';
import { TimeServer } from 'firebase-functions';

initialize();
const db = firebase.firestore();

/**
 * Picks `count` distinct winners uniformly at random from `ids` by running a partial
 * Fisher-Yates over the pool (each draw is uniform over the remaining pool, so every
 * entrant has the same chance of being picked).
 *
 * @param ids Every entrant id on the lottery's waiting list.
 * @param count How many winners the lottery asks for (clamped to the pool size).
 * @return The picked winner ids.
 */
function pickWinners(ids: string[], count: number): string[] {
  const pool = ids.slice();
  const picked: string[] = [];
  for (let i = 0; i < count && pool.length > 0; i++) {
    const j = Math.floor(Math.random() * pool.length);
    picked.push(pool[j]);
    pool[j] = pool[pool.length - 1];
    pool.pop();
  }
  return picked;
}

/** The campsite name a won notification describes, or an empty when it cannot be read. */
async function campsiteNameOf(campsiteId: string): Promise<string> {
  if (!campsiteId) {
    return '';
  }
  const res = await db.doc('campsites', campsiteId).get();
  return res.exists ? String(res.get('campName')) : '';
}

/**
 * Draws every lottery whose registration period has ended but which has not been drawn
 * yet, and notifies the winners. Time-scheduled so the draw happens while every app is
 * closed; the app side only ever reads the result (LotteryController.checkIfWon, the
 * notification inbox) so no Kotlin changes are needed for this.
 */
export const drawDueLotteries = TimeServer('everyMinute').handler(async () => {
  const dueDocs = (
    await db.collection('lotteries')
      .where('drawn', '==', false)
      .where('registrationEnd', '<=', Date.now())
      .get()
  ).docs;

  for (const res of dueDocs) {
    const lottery = res.toDict();
    const entries = (
      await db.collection('waitingList').where('lotteryId', '==', res.id).get()
    ).docs;
    const entrantIds = entries.map((entry) => String(entry.get('userId'))).filter(
      (userId) => userId !== ''
    );
    const winners = pickWinners(entrantIds, Number(lottery.numberOfWinners ?? 0));

    await res.update({ drawn: true, winnerIds: winners });

    const place = await campsiteNameOf(
      lottery.campsiteIds?.[0] ?? ''
    ).catch(() => '');
    const named = place || 'the campsite'; // same fallback as NotificationController.

    for (const userId of winners) {
      await db.doc(
        'notifications',
        `${res.id}_${userId}_WIN`
      ).set({
        recipientId: userId,
        senderId: '', // the draw is system-created, like the Kotlin model's docs say.
        type: 'WIN',
        title: `You won the lottery for ${named}!`,
        message:
          'Congratulations! You were selected in the draw for ' +
          `${named}. Action needed: open the app and choose your dates before the booking window closes.`,
        lotteryId: res.id,
        reservationId: '',
        campsiteName: place,
        status: 'UNREAD',
        pushAllowed: false, // opting out in the app is the client's decision.
        sentAt: firebase.firestore.FieldValue.serverTimestamp(),
        readAt: null,
      });
    }
  }
});
