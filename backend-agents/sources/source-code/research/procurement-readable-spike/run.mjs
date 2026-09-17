// Throwaway logged-in Codex caller. One immutable request per stage, no retries.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import {spawn} from 'node:child_process';

const stage=process.argv[2];
if(!['draft','review'].includes(stage)) throw new Error('Use draft or review');
const out=path.resolve('.workspace/procurement-readable-spike-20260916');
const base=fs.readFileSync(path.join(out,'input-v2','draft-input.txt'),'utf8');
let prompt=base;
if(stage==='review') {
  const prior=JSON.parse(fs.readFileSync(path.join(out,'draft-receipt.json'),'utf8'));
  if(prior.status!=='COMPLETED') throw new Error('DRAFT not completed');
  const draft=fs.readFileSync(path.join(out,'draft.md'),'utf8');
  prompt=base.replace('请直接输出完整 Markdown 业务文档', '本次任务是独立审阅实际草稿。请对照本次全部原始资料检查业务衔接、先后与分支、类型适用范围、字段中文含义、具体数量/状态/金额规则及读者可读性。保留正确内容，纠正不正确或不清楚的内容，返回完整修订后的文档，不返回点评或补丁。请直接输出完整 Markdown 业务文档')
    + '\n\n<实际完整草稿>\n' + draft + '\n</实际完整草稿>\n';
  fs.writeFileSync(path.join(out,'review-input.txt'),prompt,{flag:'wx'});
}
const started=new Date();
const cwd=fs.mkdtempSync(path.join(os.tmpdir(),'procurement-readable-spike-'));
const output=path.join(out,stage==='draft'?'draft.md':'reviewed.md');
const state=path.join(out,stage+'-started.json');
const args=['exec','--ephemeral','--skip-git-repo-check','--ignore-user-config','--ignore-rules','--sandbox','read-only','--model','gpt-5.6-luna','--config','model_provider="openai"','--config','forced_login_method="chatgpt"','--config','model_reasoning_effort="high"','--color','never','--json','--output-last-message',output,'-'];
const env={...process.env,CODEX_HOME:'/Users/yexiaoguang/.codex'};
for(const k of ['OPENAI_API_KEY','OPENAI_ADMIN_KEY','OPENAI_BASE_URL','OPENAI_ORG_ID','OPENAI_PROJECT_ID'])delete env[k];
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
fs.writeFileSync(state,JSON.stringify({stage,started:started.toISOString(),inputSha256:sha(prompt),inputBytes:Buffer.byteLength(prompt),model:'gpt-5.6-luna',effort:'high',auth:'ChatGPT',args},null,2),{flag:'wx'});
const stdout=fs.createWriteStream(path.join(out,stage+'-events.jsonl'),{flags:'wx'});
const stderr=fs.createWriteStream(path.join(out,stage+'-stderr.txt'),{flags:'wx'});
const child=spawn('/Applications/ChatGPT.app/Contents/Resources/codex',args,{cwd,env,stdio:['pipe','pipe','pipe']});
let events=0,buffer='';
child.stdout.on('data',chunk=>{
  stdout.write(chunk);buffer+=chunk.toString();
  let p;
  while((p=buffer.indexOf('\n'))>=0){const line=buffer.slice(0,p);buffer=buffer.slice(p+1);try{const e=JSON.parse(line);events++;if(e.type==='thread.started'||e.type==='turn.started'||e.type==='turn.completed'||e.type==='turn.failed'||e.type==='error')console.log(JSON.stringify({stage,event:e.type,usage:e.usage,error:e.error,message:e.message}));}catch{}}
});
child.stderr.on('data',chunk=>stderr.write(chunk));
child.stdin.end(prompt);
const heartbeat=setInterval(()=>console.log(JSON.stringify({stage,elapsedSeconds:Math.round((Date.now()-started.getTime())/1000),events,outputBytes:fs.existsSync(output)?fs.statSync(output).size:0})),30000);
const timeout=setTimeout(()=>child.kill('SIGTERM'),3600000);
let error=null;
child.on('error',e=>{error=e.message;});
child.on('close',(code,signal)=>{
  clearInterval(heartbeat);clearTimeout(timeout);stdout.end();stderr.end();
  const bytes=fs.existsSync(output)?fs.readFileSync(output):Buffer.alloc(0);
  const receipt={stage,status:code===0&&bytes.length>0?'COMPLETED':'FAILED',exitCode:code,signal,error,started:started.toISOString(),finished:new Date().toISOString(),elapsedSeconds:Math.round((Date.now()-started.getTime())/1000),events,inputSha256:sha(prompt),outputSha256:bytes.length?sha(bytes):null,outputBytes:bytes.length,model:'gpt-5.6-luna',effort:'high',auth:'ChatGPT'};
  fs.writeFileSync(path.join(out,stage+'-receipt.json'),JSON.stringify(receipt,null,2),{flag:'wx'});
  console.log(JSON.stringify(receipt,null,2));process.exitCode=receipt.status==='COMPLETED'?0:2;
});
