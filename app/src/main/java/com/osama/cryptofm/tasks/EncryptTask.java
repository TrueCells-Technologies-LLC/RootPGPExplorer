/*
 * Copyright (c) 2017. Osama Bin Omar
 *    This file is part of Crypto File Manager also known as Crypto FM
 *
 *     Crypto File Manager is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Crypto File Manager is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Crypto File Manager.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.osama.cryptofm.tasks;

import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import com.osama.cryptofm.CryptoFM;
import com.osama.cryptofm.encryption.DocumentFileEncryption;
import com.osama.cryptofm.encryption.EncryptionWrapper;
import com.osama.cryptofm.filemanager.listview.FileListAdapter;
import com.osama.cryptofm.filemanager.utils.SharedData;
import com.osama.cryptofm.filemanager.utils.UiUtils;
import com.osama.cryptofm.utils.FileDocumentUtils;
import com.osama.cryptofm.utils.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by home on 12/29/16.
 * encrypt file task
 */

public class EncryptTask extends AsyncTask<Void,String,String> {
    private ArrayList<String>       mFilePaths;
    private FileListAdapter         mAdapter;
    private MyProgressDialog        mProgressDialog;
    private Context                 mContext;
    private File                    pubKeyFile;

    private ArrayList<File>         mCreatedFiles=new ArrayList<>();
    private ArrayList<String>       mUnencryptedFiles=new ArrayList<>();
    private ArrayList<DocumentFile> mCreatedDocumentFiles=new ArrayList<>();

    private static final String TAG                         = EncryptTask.class.getName();
    private static final String ENCRYPTION_SUCCESS_MESSAGE  = "Successfully encrypted files";

    public EncryptTask(Context context,FileListAdapter adapter,ArrayList<String> filePaths){
        this.mAdapter           = adapter;
        this.mContext           = context;
        this.mFilePaths         = filePaths;
        this.mProgressDialog    = new MyProgressDialog(context,"Encrypting",this);
        this.pubKeyFile         = new File(mContext.getFilesDir(),"pub.asc");
    }

    @Override
    protected String doInBackground(Void... voids) {
        try {
            for (String path : mFilePaths) {
                if(!isCancelled()){
                    File f = TasksFileUtils.getFile(path);
                    if(FileUtils.isDocumentFile(path)){
                        Log.d(TAG, "doInBackground: file is not null"+f.getAbsolutePath());
                        encryptDocumentFile(FileDocumentUtils.getDocumentFile(f));
                    }else{
                        encryptFile(f);
                    }

                }

            }
            return ENCRYPTION_SUCCESS_MESSAGE;
        } catch (Exception ex) {
            ex.printStackTrace();
            return "error in encrypting file." + ex.getMessage();
        }
    }

    private void encryptFile(File f) throws Exception{
        if(!isCancelled()){
            if(f.isDirectory()){
                for (File tmp: f.listFiles()) {
                    encryptFile(tmp);
                    mFilePaths.remove(f.getAbsolutePath());
                }
            }else {
                File out = new File(f.getAbsolutePath()+".pgp");
                if(out.createNewFile()){
                    Log.d(TAG, "encryptFile: created file to encrypt into");
                }
                publishProgress(f.getName(),""+
                        ((FileUtils.getReadableSize((f.length())))));
                mCreatedFiles.add(out);
                mUnencryptedFiles.add(f.getAbsolutePath());
                EncryptionWrapper.encryptFile(f,out,pubKeyFile,true);

            }
        }


    }

    @Override
    protected void onProgressUpdate(String... values) {
        mProgressDialog.setmProgressTextViewText(values[0]);
    }

    @Override
    protected void onPostExecute(String s) {
        if(s.equals(ENCRYPTION_SUCCESS_MESSAGE)){
            deleteAlertDialog();
        }
        mProgressDialog.dismiss(s);
        Toast.makeText(
                mContext,
                s,
                Toast.LENGTH_SHORT
        ).show();
        SharedData.CURRENT_RUNNING_OPERATIONS.clear();

    }

    private void deleteAlertDialog(){
        //ask the user if he/she wants to delete the unencrypted version of file
        AlertDialog.Builder dialog=new AlertDialog.Builder(mContext);
        dialog.setMessage("Do you want to delete the unencrypted version of folders?");
        dialog.setTitle("Delete unencrypted files");
        dialog.setPositiveButton("Yes, Sure!", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

                new DeleteTask(mContext,mAdapter,mUnencryptedFiles).execute();
            }
        });
        dialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                UiUtils.reloadData(
                        mContext,
                        mAdapter
                );
            }
        });
        dialog.show();
    }

    @Override
    protected void onPreExecute() {
        mProgressDialog.show();
    }

    @Override
    protected void onCancelled() {
        if(mCreatedDocumentFiles.size()>0){
            //this means encryption was performed on document files
            for (DocumentFile get:mCreatedDocumentFiles) {
                get.delete();
            }
        }else{
            for (File f : mCreatedFiles) {
                f.delete();
            }
        }


        Toast.makeText(
                mContext,
                "Encryption canceled",
                Toast.LENGTH_SHORT
        ).show();
        SharedData.CURRENT_RUNNING_OPERATIONS.clear();
        UiUtils.reloadData(
                mContext,
                mAdapter
        );
        Log.d("cancel","yes task is canceled");
        super.onCancelled();
    }
    private DocumentFile rootDocumentFile;
    private void encryptDocumentFile(DocumentFile file) throws FileNotFoundException {
        Log.d(TAG, "encryptDocumentFile: encrypting document file");
        if(file.isDirectory()){
            rootDocumentFile=file;
            for (DocumentFile f:file.listFiles()) {
                encryptDocumentFile(f);
            }
        }else{
            //check if root document is not null
            if(rootDocumentFile==null){
                String path=mFilePaths.get(0).substring(0,mFilePaths.get(0).lastIndexOf('/'));

                Log.d(TAG, "encryptDocumentFile: Getting the root document: "+path);
                rootDocumentFile=FileDocumentUtils.getDocumentFile(new File(path));
            }
            DocumentFile temp=rootDocumentFile.createFile("pgp", file.getName()+".pgp");
            mCreatedDocumentFiles.add(temp);
            publishProgress(file.getName(),""+
                    ((FileUtils.getReadableSize((file.length())))));

            InputStream in= CryptoFM.getContext().getContentResolver().openInputStream(file.getUri());
            OutputStream out=CryptoFM.getContext().getContentResolver().openOutputStream(temp.getUri());
            DocumentFileEncryption.encryptFile(in,out,pubKeyFile,true,new Date(file.lastModified()),file.getName());

        }
    }
}