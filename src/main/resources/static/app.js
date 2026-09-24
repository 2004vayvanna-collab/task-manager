const $ = s => document.querySelector(s);
let tasks = [], filter = 'all', editing = null, registering = false, csrf = null;
async function refreshCsrf() {
 const response = await fetch('/api/auth/csrf');
 if (!response.ok) throw new Error('Could not connect. Refresh and try again.');
 csrf = await response.json();
}
function showSignedOut() {
 tasks = []; editing = null; $('#tasks').replaceChildren(); $('#dashboard').hidden = true;
 $('#account').hidden = true; $('#auth-panel').hidden = false; $('#auth-password').value = '';
}
async function request(path, options = {}) {
 const headers = {...options.headers};
 if (options.body && !(options.body instanceof URLSearchParams)) headers['Content-Type'] = 'application/json';
 if (options.method && options.method !== 'GET') {
  if (!csrf) await refreshCsrf();
  headers[csrf.headerName] = csrf.token;
 }
 const response = await fetch(path, {...options, headers});
 if (!response.ok) {
  if (response.status === 401 && path !== '/api/auth/login') showSignedOut();
  if (response.status === 403) { await refreshCsrf(); throw new Error('Your session changed. Please sign in or try again.'); }
  const errors = {400:'Check your input. Use a valid title, priority, date, or account details.',401:path === '/api/auth/login' ? 'Incorrect username or password.' : 'Please sign in again.',409:'That username is already taken.',404:'Task no longer available. Refresh your list.'};
  throw new Error(errors[response.status] || `Request failed (${response.status}). Please try again.`);
 }
 return response.status === 204 ? null : response.json();
}
function localDate() {
 const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}
function resetEditor() {
 editing = null; $('#task-form').reset(); $('#save-task').textContent = '+ Add task'; $('#cancel-edit').hidden = true;
}
function editTask(task) {
 editing = task; $('#title').value = task.title; $('#priority').value = task.priority;
 $('#due-date').value = task.dueDate || ''; $('#save-task').textContent = 'Save changes'; $('#cancel-edit').hidden = false; $('#title').focus();
}
function render() {
 $('#total').textContent = tasks.length; $('#done').textContent = tasks.filter(t=>t.completed).length;
 $('#open').textContent = tasks.filter(t=>!t.completed).length;
 const visible = tasks.filter(t=>filter==='all'||(filter==='done'?t.completed:!t.completed));
 $('#count').textContent = `${visible.length} shown`; $('#tasks').replaceChildren();
 $('#empty').hidden = visible.length > 0; $('#empty').textContent = tasks.length?'No tasks in this view.':'Your next step starts here. Add your first task above.';
 for (const task of visible) {
  const row = document.createElement('li'); row.classList.toggle('completed',task.completed);
  const toggle = document.createElement('button'); toggle.className='toggle';toggle.textContent=task.completed?'✓':'';
  toggle.setAttribute('aria-label',`Mark ${task.title} ${task.completed?'in progress':'complete'}`);
  toggle.onclick=()=>action(toggle,()=>request('/api/tasks/'+task.id,{method:'PUT',body:JSON.stringify({...task,completed:!task.completed})}));
  const body=document.createElement('div');body.className='task-body';
  const title=document.createElement('span');title.className='task-title';title.textContent=task.title;body.append(title);
  if(task.dueDate){const due=document.createElement('span');due.className='due';const late=!task.completed&&task.dueDate<localDate();due.classList.toggle('overdue',late);due.textContent=(late?'Overdue · ':'Due · ')+task.dueDate;body.append(due);}
  const priority=document.createElement('span');priority.className='priority '+task.priority.toLowerCase();priority.textContent=task.priority;
  const buttons=document.createElement('div');buttons.className='row-actions';
  const edit=document.createElement('button');edit.className='edit';edit.textContent='Edit';edit.setAttribute('aria-label','Edit '+task.title);edit.onclick=()=>editTask(task);
  const remove=document.createElement('button');remove.className='delete';remove.textContent='Delete';remove.setAttribute('aria-label','Delete '+task.title);
  remove.onclick=()=>action(remove,async()=>{await request('/api/tasks/'+task.id,{method:'DELETE'});if(editing?.id===task.id)resetEditor();});
  buttons.append(edit,remove);row.append(toggle,body,priority,buttons);$('#tasks').append(row);
 }
}
async function load(){tasks=await request('/api/tasks');render();}
async function action(button,callback){
 button.disabled=true;$('#message').textContent='';
 try{await callback();await load();}catch(e){$('#message').textContent=e.message;if($('#dashboard').hidden)$('#auth-message').textContent=e.message;}
 finally{button.disabled=false;}
}
$('#task-form').onsubmit=async e=>{
 e.preventDefault();const title=$('#title').value.trim();if(!title){$('#message').textContent='Please enter a task title.';return;}
 const data={title,priority:$('#priority').value,dueDate:$('#due-date').value||null};
 const current=editing&&tasks.find(t=>t.id===editing.id);
 if(editing&&!current){resetEditor();$('#message').textContent='Task no longer available.';return;}
 if(current)data.completed=current.completed;
 await action($('#save-task'),async()=>{await request('/api/tasks'+(current?'/'+current.id:''),{method:current?'PUT':'POST',body:JSON.stringify(data)});resetEditor();});
};
$('#cancel-edit').onclick=resetEditor;
document.querySelectorAll('[data-filter]').forEach(button=>button.onclick=()=>{filter=button.dataset.filter;document.querySelectorAll('[data-filter]').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));render();});
$('#auth-switch').onclick=()=>{
 registering=!registering;$('#auth-heading').textContent=registering?'Create your account':'Welcome back';
 $('#auth-help').textContent=registering?'Username: 3–40 lowercase letters, numbers or underscores. Password: 10–72 characters (maximum 72 UTF-8 bytes).':'Sign in to your personal task list.';
 $('#auth-submit').textContent=registering?'Create account':'Sign in';$('#auth-switch').textContent=registering?'Already have an account? Sign in':'Create an account';
 $('#auth-password').autocomplete=registering?'new-password':'current-password';$('#auth-password').minLength=registering?10:1;$('#auth-message').textContent='';
};
$('#auth-form').onsubmit=async e=>{
 e.preventDefault();const button=$('#auth-submit');button.disabled=true;$('#auth-message').textContent='';
 const username=$('#auth-user').value,password=$('#auth-password').value;
 try{
  if(registering)await request('/api/auth/register',{method:'POST',body:JSON.stringify({username,password})});
  await request('/api/auth/login',{method:'POST',body:new URLSearchParams({username,password})});
  await refreshCsrf();await signedIn();
 }catch(error){$('#auth-message').textContent=error.message;}finally{button.disabled=false;}
};
async function signedIn(){
 const user=await request('/api/auth/me');$('#username').textContent=user.username;$('#auth-password').value='';
 resetEditor();filter='all';document.querySelectorAll('[data-filter]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.filter==='all')));
 await load();$('#auth-panel').hidden=true;$('#dashboard').hidden=false;$('#account').hidden=false;
}
$('#logout').onclick=async()=>{
 try{await request('/api/auth/logout',{method:'POST'});showSignedOut();await refreshCsrf();}catch(e){$('#message').textContent=e.message;}
};
(async()=>{try{await refreshCsrf();const r=await fetch('/api/auth/me');if(r.ok)await signedIn();else if(r.status!==401)throw new Error('Could not connect to the application.');}catch(e){$('#auth-message').textContent=e.message;}})();
