package com.example.whatsapp.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;

import com.bumptech.glide.Glide;
import com.example.whatsapp.adapter.MensagensAdapter;
import com.example.whatsapp.config.ConfiguracaoFireBase;
import com.example.whatsapp.helper.Base64Custom;
import com.example.whatsapp.helper.UsuarioFirebase;
import com.example.whatsapp.model.Conversa;
import com.example.whatsapp.model.Grupo;
import com.example.whatsapp.model.Mensagem;
import com.example.whatsapp.model.Usuario;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.whatsapp.R;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatActivity extends AppCompatActivity {

    private TextView textViewNome;
    private CircleImageView circleImageViewFoto;
    private Usuario usuarioDestinatario;
    private EditText editMensagem;
    private ImageView imageCamera;
    private RecyclerView recyclerMensagens;
    private MensagensAdapter adapter;
    private List<Mensagem> listaMensagens =  new ArrayList<>();

    private static final int SELECAO_CAMERA = 100;

    // identificador usuarios rementente e destinatario
    private String idUsuarioRemetente;
    private String idUsuarioDestinatario;

    private DatabaseReference database;
    private DatabaseReference mensagensRef;
    private StorageReference storageReference;
    private ChildEventListener childEventListenerMensagens;
    private Grupo grupo;
    private Usuario usuarioRemetente;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        editMensagem = findViewById(R.id.editMensagem);
        textViewNome = findViewById(R.id.textNomeChat);
        imageCamera = findViewById(R.id.imageCamera);
        circleImageViewFoto = findViewById(R.id.circleImageFotoChat);
        recyclerMensagens = findViewById(R.id.recyclerMensagens);

        idUsuarioRemetente = UsuarioFirebase.getIdUsuario();
        usuarioRemetente = UsuarioFirebase.getDadosUsuarioLogado();

        //recupera os dados do usuario destinatario
        Bundle bundle = getIntent().getExtras();
        if (bundle != null){

            if (bundle.containsKey("chatGrupo")){
                grupo = (Grupo) bundle.getSerializable("chatGrupo");
                idUsuarioDestinatario = grupo.getId();
                textViewNome.setText(grupo.getNome());
                String foto = grupo.getFoto();
                if (foto != null){
                    Uri url = Uri.parse(foto);
                    Glide.with(ChatActivity.this)
                            .load(url).into( circleImageViewFoto);
                }
                else {
                    circleImageViewFoto.setImageResource(R.drawable.padrao);
                }
            }
            else {
                usuarioDestinatario = (Usuario) bundle.getSerializable("chatContato");
                textViewNome.setText(usuarioDestinatario.getNome());
                String foto = usuarioDestinatario.getFoto();
                if (foto != null){
                    Uri url = Uri.parse(foto);
                    Glide.with(ChatActivity.this)
                            .load(url).into( circleImageViewFoto);
                }
                else {
                    circleImageViewFoto.setImageResource(R.drawable.padrao);
                }

                idUsuarioDestinatario = Base64Custom.codificarBase64( usuarioDestinatario.getEmail());
            }
        }
        // config Adapter
        adapter = new MensagensAdapter( listaMensagens,getApplicationContext());

        // config recycler
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerMensagens.setLayoutManager(layoutManager);
        recyclerMensagens.setHasFixedSize(true);
        recyclerMensagens.setAdapter(adapter);

        database = ConfiguracaoFireBase.getFirebaseDataBase();
        storageReference = ConfiguracaoFireBase.getFirebaseStorage();

        mensagensRef = database.child("mensagens")
                .child(idUsuarioRemetente)
                .child(idUsuarioDestinatario);

        // evento de clique na camera
        imageCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (intent.resolveActivity(getPackageManager()) != null){
                    startActivityForResult(intent, SELECAO_CAMERA);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_OK){
            Bitmap imagem = null;

            try {
                switch (requestCode){
                    case SELECAO_CAMERA:
                        imagem = (Bitmap) data.getExtras().get("data");
                        break;
                }
            }catch (Exception e){
                e.printStackTrace();
            }

            if (imagem != null){

                //Recuperar dados da imagem para o firebase
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                imagem.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                byte[] dadosImagem = baos.toByteArray();

                // criar nome da imagem
                String nomeImagem = UUID.randomUUID().toString();

                // config referencia do firebase
                final StorageReference imagemRef = storageReference.child("imagens")
                        .child("fotos")
                        .child(idUsuarioRemetente).child(nomeImagem + ".jpeg");

                UploadTask uploadTask = imagemRef.putBytes(dadosImagem);
                uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("Erro", "Erro ao fazer upload da imagem");
                        Toast.makeText(ChatActivity.this, "Erro ao fazer upload da imagem",
                                Toast.LENGTH_LONG).show();
                    }
                }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        String downloadUrl = imagemRef.getDownloadUrl().addOnCompleteListener(new OnCompleteListener<Uri>() {
                            @Override
                            public void onComplete(@NonNull Task<Uri> task) {
                                Uri url = task.getResult(); // aqui recupera a url da imagem
                            }
                        }).toString();

                        if (usuarioDestinatario != null){ // mensagem nomal
                            Mensagem mensagem = new Mensagem();
                            mensagem.setIdUsuario(idUsuarioRemetente);
                            mensagem.setMensagem("imagem.jpeg");
                            mensagem.setImagem(downloadUrl);
                            // salvar para o remetente
                            salvarMensagem(idUsuarioRemetente, idUsuarioDestinatario, mensagem);

                            // salvar para o destinatario
                            salvarMensagem(idUsuarioDestinatario, idUsuarioRemetente,  mensagem);
                        }
                        else {// mensagem em grupo

                            for (Usuario membro: grupo.getMembros()){
                                String idRemetenteGrupo = Base64Custom.codificarBase64(membro.getEmail());
                                String idUsuarioLogadoGrupo = UsuarioFirebase.getIdUsuario();

                                Mensagem mensagem = new Mensagem();
                                mensagem.setIdUsuario(idUsuarioLogadoGrupo);
                                mensagem.setMensagem("imagem.jpeg");
                                mensagem.setNome(usuarioRemetente.getNome());
                                mensagem.setImagem(downloadUrl);

                                // salvar mensagem para o membro
                                salvarMensagem(idRemetenteGrupo, idUsuarioDestinatario, mensagem);

                                //salvar conversa grupo
                                salvarConversa(idRemetenteGrupo,idUsuarioDestinatario, usuarioDestinatario, mensagem, true);
                            }
                        }
                        Toast.makeText(ChatActivity.this, "Sucesso ao enviar imagem!",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    }

    public void enviarMensagem(View view){
        String textoMensagem = editMensagem.getText().toString();

        if (usuarioDestinatario != null){
            if (!textoMensagem.isEmpty()){
                Mensagem mensagem = new Mensagem();
                mensagem.setIdUsuario(idUsuarioRemetente);
                mensagem.setMensagem(textoMensagem);

                // salvar mensagem remetente
                salvarMensagem(idUsuarioRemetente, idUsuarioDestinatario, mensagem);

                // salvar mensagem Destinatario
                salvarMensagem(idUsuarioDestinatario, idUsuarioRemetente, mensagem);

                // salvar conversa remetente
                salvarConversa(idUsuarioRemetente, idUsuarioDestinatario, usuarioDestinatario, mensagem, false);

                //salvar conversa destinatario
                salvarConversa(idUsuarioDestinatario, idUsuarioRemetente, usuarioRemetente, mensagem, false);
            }
            else {
                Toast.makeText(ChatActivity.this, "Digite uma mensagem para enviar!", Toast.LENGTH_LONG).show();
            }
        }
        else{
            for (Usuario membro: grupo.getMembros()){
                String idRemetenteGrupo = Base64Custom.codificarBase64(membro.getEmail());
                String idUsuarioLogadoGrupo = UsuarioFirebase.getIdUsuario();

                Mensagem mensagem = new Mensagem();
                mensagem.setIdUsuario(idUsuarioLogadoGrupo);
                mensagem.setMensagem(textoMensagem);
                mensagem.setNome(usuarioRemetente.getNome());

                // salvar mensagem para o membro
                salvarMensagem(idRemetenteGrupo, idUsuarioDestinatario, mensagem);

                //salvar conversa grupo
                salvarConversa(idRemetenteGrupo,idUsuarioDestinatario, usuarioDestinatario, mensagem, true);
            }
        }

    }

    private void salvarConversa(String idRemetente, String idDestinatario, Usuario usuarioExibicao, Mensagem msg, boolean isGroup){

        Conversa conversaRemetente = new Conversa();
        conversaRemetente.setIdRemetente(idRemetente);
        conversaRemetente.setIdDestinartario(idDestinatario);
        conversaRemetente.setUltimaMensagem(msg.getMensagem());

        if (isGroup){ // conversa de grupo
            conversaRemetente.setIsGroup("true");
            conversaRemetente.setGrupo(grupo);
        }
        else{ // conversa normal
            conversaRemetente.setUsuarioExibicao(usuarioExibicao);
            conversaRemetente.setIsGroup("false");
        }

        conversaRemetente.salvar();
    }

    private void salvarMensagem(String idRemetente, String idDestinatario, Mensagem msg){
        DatabaseReference database = ConfiguracaoFireBase.getFirebaseDataBase();
        DatabaseReference mensagemRef = database.child("mensagens");

        mensagemRef.child(idRemetente)
                .child(idDestinatario)
                .push()
                .setValue(msg);

        editMensagem.setText(""); // limpar caixa de texto
    }

    @Override
    protected void onStart() {
        super.onStart();
        recuperarMensagem();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mensagensRef.removeEventListener(childEventListenerMensagens);
    }

    private void recuperarMensagem(){

        listaMensagens.clear();
        childEventListenerMensagens = mensagensRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Mensagem mensagem = snapshot.getValue(Mensagem.class);
                listaMensagens.add(mensagem);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }
}