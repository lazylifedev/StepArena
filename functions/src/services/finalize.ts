import {Firestore, Timestamp} from 'firebase-admin/firestore';
import {competitionSteps, rewardSteps} from '../domain/models';

export async function finalizeExpiredChallenges(db: Firestore, now = Timestamp.now()): Promise<number> {
  const snap = await db.collection('challenges').where('status', '==', 'active').where('endsAt', '<=', now).limit(100).get();
  let finalized = 0;
  for (const challenge of snap.docs) {
    const changed = await db.runTransaction(async tx => {
      const current = (await tx.get(challenge.ref)).data();
      const ids = current?.participantIds;
      if (!current || current.status !== 'active' || !Array.isArray(ids) || ids.length !== 2 || ids.some(id => typeof id !== 'string')) return false;
      const participants = await Promise.all(ids.map(uid => tx.get(challenge.ref.collection('participants').doc(uid))));
      if (participants.some(p => !p.exists || !Number.isInteger(p.data()?.officialSteps) || (p.data()?.officialSteps as number) < 0)) return false;
      const values = participants.map(p => ({uid: p.id, steps: competitionSteps(p.data()?.officialSteps as number)}));
      const max = Math.max(...values.map(v => v.steps));
      const winners = values.filter(v => v.steps === max).map(v => v.uid);
      tx.update(challenge.ref, {status: 'finalized', winnerUid: winners.length === 1 ? winners[0] : null, finalizedAt: now, updatedAt: now});
      for (const v of values) tx.update(challenge.ref.collection('participants').doc(v.uid), {competitionSteps: v.steps, rewardSteps: rewardSteps(v.steps), result: winners.length === 1 ? (winners.includes(v.uid) ? 'win' : 'loss') : 'draw', syncState: 'finalized', progressUpdatedAt: now});
      return true;
    });
    if (changed) finalized++;
  }
  return finalized;
}
