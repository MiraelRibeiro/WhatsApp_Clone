package com.example.whatsapp.activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.whatsapp.R;
import com.example.whatsapp.config.ConfiguracaoFireBase;
import com.example.whatsapp.helper.Permissao;
import com.example.whatsapp.helper.UsuarioFirebase;
import com.example.whatsapp.model.Usuario;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;

import de.hdodenhof.circleimageview.CircleImageView;

public class ConfiuracoesActivity extends AppCompatActivity {

    private String[] permissoesNecessarias = new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    private StorageReference storageReference;

    private ImageButton imageButtonCamera, imageButtonGaleria;
    private CircleImageView circleImageViewPerfil;
    private EditText editNomePerfil;
    private ImageView imagemAtualizaNome;

    private static final int SELECAO_CAMERA = 100;
    private static final int SELECAO_GALERIA = 200;
    private String idUsuario;

    private Usuario usuarioLogado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confiuracoes);

        // configurações iniciais
        idUsuario = UsuarioFirebase.getIdUsuario();
        storageReference = ConfiguracaoFireBase.getFirebaseStorage();
        usuarioLogado = UsuarioFirebase.getDadosUsuarioLogado();

        // validar permissoes
        Permissao.validarPermissoes(permissoesNecessarias, this, 1);

        imageButtonCamera = findViewById(R.id.imageButtonCamera);
        imageButtonGaleria = findViewById(R.id.imageButtonFoto);
        imagemAtualizaNome = findViewById(R.id.imageAtualizarNome);
        circleImageViewPerfil = findViewById(R.id.circleImageViewFotoPerfil);
        editNomePerfil = findViewById(R.id.editPerfilNome);

        Toolbar toolbar = findViewById(R.id.toolbarPrincipal);
        toolbar.setTitle("Configurações");
        setSupportActionBar(toolbar);

        // solicitar permissao
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //recupera dados do usuario
        FirebaseUser usuario = UsuarioFirebase.getUsuarioAtual();
        Uri url =  usuario.getPhotoUrl();

        if (url != null){
            Glide.with(ConfiuracoesActivity.this)
            .load(url).into(circleImageViewPerfil);
        }
        else {
            circleImageViewPerfil.setImageResource(R.drawable.padrao);
        }
        editNomePerfil.setText(usuario.getDisplayName());

        imageButtonCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (intent.resolveActivity(getPackageManager()) != null){
                    startActivityForResult(intent, SELECAO_CAMERA);
                }
            }
        });

        imageButtonGaleria.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                if (intent.resolveActivity(getPackageManager()) != null){
                    startActivityForResult(intent, SELECAO_GALERIA);
                }
            }
        });

        imagemAtualizaNome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String nome = editNomePerfil.getText().toString();
                boolean retorno = UsuarioFirebase.atualizarNomeUsuatio(nome);
                if (retorno){
                    usuarioLogado.setNome(nome);
                    usuarioLogado.atualizar();
                    Toast.makeText(ConfiuracoesActivity.this, "Nome alterado com sucesso!", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK){
            Bitmap imagem = null;

            try {
                switch (requestCode){
                    case SELECAO_CAMERA:
                        imagem = (Bitmap) data.getExtras().get("data");
                        break;
                    case SELECAO_GALERIA:
                        Uri localImagemSelecionada = data.getData();
                        imagem = MediaStore.Images.Media.getBitmap(getContentResolver(), localImagemSelecionada);
                        break;
                }

                if (imagem != null){
                    circleImageViewPerfil.setImageBitmap(imagem);

                    //Recuperar dados da imagem para o firebase
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    imagem.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                    byte[] dadosImagem = baos.toByteArray();

                    // salvar a imagem no firebase
                    final StorageReference imagemRef = storageReference
                            .child("imagens")
                            .child("perfil")
                            .child(idUsuario + "jpeg");
                            //.child("perfil.jpeg");

                    UploadTask uploadTask = imagemRef.putBytes(dadosImagem);
                    uploadTask.addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(ConfiuracoesActivity.this, "Erro ao fazer upload da imagem!",
                                    Toast.LENGTH_LONG).show();
                        }
                    }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            Toast.makeText(ConfiuracoesActivity.this, "Sucesso ao fazer upload da imagem!",
                                    Toast.LENGTH_LONG).show();

                            imagemRef.getDownloadUrl().addOnCompleteListener(new OnCompleteListener<Uri>() {
                                @Override
                                public void onComplete(@NonNull Task<Uri> task) {
                                    Uri url = task.getResult();
                                    atualizaFotoUsuario(url);
                                }
                            });
                        }
                    });

                }

            }catch (Exception e){
                e.printStackTrace();
            }
        }
        else {
            Toast.makeText(getApplicationContext(),"Erro ao recuperar dados de camera/galeria!", Toast.LENGTH_LONG).show();
        }
    }

    public void atualizaFotoUsuario(Uri url){
        Boolean retorno = UsuarioFirebase.atualizarFotoUsuatio(url);
        if (retorno){
            usuarioLogado.setFoto(url.toString());
            usuarioLogado.atualizar();
            Toast.makeText(getApplicationContext(),"Foto alterada com sucesso!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        for(int permissaoResultado : grantResults){
            if (permissaoResultado == PackageManager.PERMISSION_DENIED){
                alertaValidacaoPermissao();
            }
        }
    }

    private void alertaValidacaoPermissao(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permissões Negadas");
        builder.setMessage("Para utilizar o app é necessário aceitar as permissões!");
        builder.setCancelable(false);
        builder.setPositiveButton("Confirmar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                finish();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

}