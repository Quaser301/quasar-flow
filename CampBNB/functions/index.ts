/**
 * NOTICE: This file was AI-assisted. The verbatim prompts for this work are recorded in
 * ../../AI_Disclosure/Wyatt_AI_Use.md.
 *
 * US 02.05.01 executed under the assumed Blaze plan: after a lottery's registration
 * period ends, winners are drawn and notified without opening the app and without any
 * owner action. The design and the no-Blaze investigation are documented in
 * ../../../documentation/AutomaticDraws.md; the wire fields here mirror the Kotlin data
 * classes exactly (models/Lottery.kt, models/WaitingListEntry.kt, models/Notification.kt).
 */

import admin from 'firebase-admin';
import { onSchedule } from 'firebase-functions/scheduler';

admin.initializeApp();
const db = admin.firestore();

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
  const res = await db.doc(`campsites/${campsiteId}`).get();

  return res.exists ? String(res.get('campName')) : '';
}

/**
 * Claims and draws one due lottery inside a Firestore transaction: the transaction
 * re-reads the lottery and only sets drawn=true if it is still false, so even if two
 * runs ever see the same due lottery, the winner set is decided exactly once.
 * Notification records carry deterministic ids, so their writes stay idempotent.
 *
 * @param lotteryId The lottery to draw.
 * @param numberOfWinners How many winners the lottery asks for.
 * @return The winners that were claimed, or an empty list if another run drew first.
 */
async function drawAndClaim(lotteryId: string, numberOfWinners: number): Promise<string[]> {
  const entries = await db
    .collection('waitingList')
    .where('lotteryId', '==', lotteryId)
    .get();
  const entrantIds = entries.docs
    .map((entry) => String(entry.get('userId')))
    .filter((userId) => userId !== '');
  const winners = pickWinners(entrantIds, numberOfWinners);

  const claimed = await db.runTransaction(async (tx: admin.firestore.Transaction) => {
    const docRef = db.doc(`lotteries/${lotteryId}`)
    const current = await tx.get(docRef);
    if (current.get('drawn') === true) {
      return false;
    }
    tx.update(docRef, { drawn: true, winnerIds: winners });
    return true;
  });

  return claimed ? winners : [];
}

/**
 * Draws every lottery whose registration period has ended but which has not been drawn
 * yet, and writes one WIN notification record per winner. Time-scheduled so the draw
 * happens while every app is closed; the app side only ever reads the result
 * (LotteryController.checkIfWon, the notification inbox), so no Kotlin changes are
 * needed. One failed lottery must not leave the later due ones behind, so each is
 * isolated.
 */
export const drawDueLotteries = onSchedule('everyMinute', async () => {
  const dueDocs = (
    await db.collection('lotteries')
      .where('drawn', '==', false)
      .where('registrationEnd', '<=', Date.now())
      .get()
  ).docs;

  for (const res of dueDocs) {
    try {
      const winners = await drawAndClaim(
        res.id,
        Number(res.get('numberOfWinners') ?? 0)
      );
      if (winners.length === 0) {
        continue;
      }
      const campsiteIds = res.get('campsiteIds') as string[];
      const place = await campsiteNameOf(campsiteIds?.[0] ?? '');
      const named = place || 'the campsite'; // same fallback as NotificationController.

      for (const userId of winners) {

        await db.doc(`notifications/${res.id}_${userId}_WIN`).set({
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
          sentAt: admin.firestore.Timestamp.fromMillis(Date.now()),
          readAt: null,
        });
      }
      console.log(`drew ${winners.length} winners for ${res.id}`);
    } catch (error) {
      console.error(`draw failed for ${res.id}:`, error);
    }
  }
});
