const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { describe, it, before, after, beforeEach } = require('node:test');

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-apexledger',
    firestore: { rules: readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8') },
  });
});

after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc('users/client1').set({ role: 'client', fullName: 'Client One' });
    await db.doc('users/client2').set({ role: 'client', fullName: 'Client Two' });
    await db.doc('users/acct1').set({ role: 'accountant', fullName: 'Accountant' });
    await db.doc('users/mgr1').set({ role: 'manager', fullName: 'Manager' });
    await db.doc('users/admin1').set({ role: 'admin', fullName: 'Admin' });
    await db.doc('tickets/t1').set({ clientId: 'client1', status: 'open', description: 'Tax return' });
    await db.doc('ticket_notes/t1').set({ note: 'internal only' });
    await db.doc('documents/d1').set({ clientId: 'client1', reviewStatus: 'pending', uploadComplete: true });
    await db.doc('notifications/n1').set({ recipientUid: 'client1', senderUid: 'acct1', isRead: false });
    await db.doc('threads/th1').set({ clientId: 'client1' });
    await db.doc('threads/th1/messages/m1').set({ senderId: 'client1', messageBody: 'hi', readBy: ['client1'] });
  });
});

const as = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();
const auditEntry = (actorUid, action = 'Login') =>
  ({ actorUid, userType: 'Client', action, details: '', timestamp: new Date() });

describe('Signed-out visitors', () => {
  it('cannot read users', async () => {
    await assertFails(anon().doc('users/client1').get());
  });
  it('can only write a Failed Login audit entry', async () => {
    await assertSucceeds(anon().collection('audit_log').add(auditEntry('', 'Failed Login')));
    await assertFails(anon().collection('audit_log').add(auditEntry('', 'Login')));
  });
});

describe('Client', () => {
  it('can read own profile but not another client', async () => {
    await assertSucceeds(as('client1').doc('users/client1').get());
    await assertFails(as('client1').doc('users/client2').get());
  });
  it('can read own ticket but another client cannot', async () => {
    await assertSucceeds(as('client1').doc('tickets/t1').get());
    await assertFails(as('client2').doc('tickets/t1').get());
  });
  it('cannot read internal ticket notes', async () => {
    await assertFails(as('client1').doc('ticket_notes/t1').get());
  });
  it('cannot change own ticket status but can edit the description', async () => {
    await assertFails(as('client1').doc('tickets/t1').update({ status: 'completed' }));
    await assertSucceeds(as('client1').doc('tickets/t1').update({ description: 'Updated' }));
  });
  it('can create an open ticket but not a completed one', async () => {
    await assertSucceeds(as('client1').doc('tickets/t2').set({ clientId: 'client1', status: 'open' }));
    await assertFails(as('client1').doc('tickets/t3').set({ clientId: 'client1', status: 'completed' }));
  });
  it('cannot create a ticket for another client', async () => {
    await assertFails(as('client1').doc('tickets/t4').set({ clientId: 'client2', status: 'open' }));
  });
  it('can upload a pending document but not an approved one', async () => {
    await assertSucceeds(as('client1').doc('documents/d2')
      .set({ clientId: 'client1', reviewStatus: 'pending', uploadComplete: false }));
    await assertFails(as('client1').doc('documents/d3')
      .set({ clientId: 'client1', reviewStatus: 'approved', uploadComplete: false }));
  });
  it('cannot approve own document', async () => {
    await assertFails(as('client1').doc('documents/d1').update({ reviewStatus: 'approved' }));
  });
  it('cannot promote own role to admin', async () => {
    await assertFails(as('client1').doc('users/client1').update({ role: 'admin' }));
  });
  it('cannot read the audit log', async () => {
    await assertFails(as('client1').collection('audit_log').get());
  });
  it('cannot forge an audit entry in another user\'s name', async () => {
    await assertFails(as('client1').collection('audit_log').add(auditEntry('admin1')));
    await assertSucceeds(as('client1').collection('audit_log').add(auditEntry('client1')));
  });
  it('cannot spoof a notification sender', async () => {
    const note = (sender) => ({
      recipientUid: 'acct1', senderUid: sender, type: 'x', title: 't', body: 'b',
      targetId: '', isRead: false, createdAt: new Date(),
    });
    await assertFails(as('client1').collection('notifications').add(note('admin1')));
    await assertSucceeds(as('client1').collection('notifications').add(note('client1')));
  });
  it('cannot read someone else\'s notification, and can only mark own as read', async () => {
    await assertFails(as('client2').doc('notifications/n1').get());
    await assertFails(as('client1').doc('notifications/n1').update({ recipientUid: 'client2' }));
    await assertSucceeds(as('client1').doc('notifications/n1').update({ isRead: true }));
  });
  it('cannot edit message text but can mark it read', async () => {
    await assertFails(as('client1').doc('threads/th1/messages/m1').update({ messageBody: 'changed' }));
    await assertSucceeds(as('client1').doc('threads/th1/messages/m1')
      .update({ readBy: ['client1', 'acct1'] }));
  });
  it('another client cannot read a chat thread', async () => {
    await assertFails(as('client2').doc('threads/th1').get());
  });
});

describe('Accountant', () => {
  it('can read a client ticket and its internal notes', async () => {
    await assertSucceeds(as('acct1').doc('tickets/t1').get());
    await assertSucceeds(as('acct1').doc('ticket_notes/t1').get());
  });
  it('can update ticket status and approve documents', async () => {
    await assertSucceeds(as('acct1').doc('tickets/t1').update({ status: 'in_progress' }));
    await assertSucceeds(as('acct1').doc('documents/d1').update({ reviewStatus: 'approved' }));
  });
  it('can read the audit log but cannot edit or delete entries', async () => {
    await assertSucceeds(as('acct1').collection('audit_log').get());
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc('audit_log/a1').set(auditEntry('client1'));
    });
    await assertFails(as('acct1').doc('audit_log/a1').update({ action: 'Edited' }));
    await assertFails(as('acct1').doc('audit_log/a1').delete());
  });
  it('cannot change another user\'s role', async () => {
    await assertFails(as('acct1').doc('users/client1').update({ role: 'admin' }));
  });
});

describe('Manager', () => {
  it('can review documents but cannot upload one on behalf of a client', async () => {
    await assertSucceeds(as('mgr1').doc('documents/d1').update({ reviewStatus: 'approved' }));
    await assertFails(as('mgr1').doc('documents/d9')
      .set({ clientId: 'client1', reviewStatus: 'approved', uploadComplete: false }));
  });
});

describe('Admin', () => {
  it('can change roles and delete users', async () => {
    await assertSucceeds(as('admin1').doc('users/client2').update({ role: 'accountant' }));
    await assertSucceeds(as('admin1').doc('users/client2').delete());
  });
});